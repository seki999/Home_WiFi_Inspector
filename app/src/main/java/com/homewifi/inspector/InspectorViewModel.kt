package com.homewifi.inspector
import androidx.lifecycle.*
import com.homewifi.inspector.domain.*
import com.homewifi.inspector.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@HiltViewModel
class InspectorViewModel @Inject constructor(private val locator:GeoLocator,private val locationsDao:LocationDao,private val downloader:DownloadTester,private val source:WifiSource,private val tester:NetworkTester,private val dao:SessionDao,private val settings:Settings):ViewModel() {
 val snapshot=MutableStateFlow(WifiSnapshot())
 val samples=MutableStateFlow<List<WifiSnapshot>>(emptyList())
 val scan=MutableStateFlow(ScanState())
 val events=MutableStateFlow<List<String>>(emptyList())
 val message=MutableStateFlow("")
 val testResult=MutableStateFlow("尚未检测")
 val busy=MutableStateFlow(false)
 val downloadResult=MutableStateFlow<DownloadResult?>(null)
 val downloadStatus=MutableStateFlow("尚未测速")
 private var downloadNetwork:Pair<String?,List<String>>?=null
 val geo=MutableStateFlow<GeoFix?>(null)
 val geoStatus=MutableStateFlow("尚未附加系统定位；可仅使用手动位置名称")
 val locating=MutableStateFlow(false)
 private var geoJob:Job?=null
 private var testNetwork:String?=null
 private var scanTimeout:Job?=null
 private var lastGatewayCheck:Long?=null
 val paused=MutableStateFlow(false)
 val interval=settings.intervalMs.stateIn(viewModelScope,SharingStarted.Eagerly,2000L)
 val history=dao.observe().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 val locations=locationsDao.observe().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 private var monitor:Job?=null
 private var testing:Job?=null
 private var latestGateway:LatencyStats?=null
 private var latestInternet:LatencyStats?=null
 fun foreground(active:Boolean) {
  monitor?.cancel();monitor=null
  if(!active || paused.value) return
  monitor=viewModelScope.launch {
   launch { source.changes().catch { message.value="网络回调不可用：${it.message}" }.collect { events.value=(events.value+"${java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.getDefault()).format(java.util.Date())} $it").takeLast(50) } }
   launch { source.scans().catch { message.value="扫描监听失败：${it.message}" }.collect { scan.value=it;if(message.value.startsWith("扫描请求") || message.value.startsWith("等待扫描结果")) { message.value=it.message;scanTimeout?.cancel() } } }
   while(isActive) {
    try {
     val s=source.read();val previous=snapshot.value
     if(testNetwork!=null && s.networkId!=testNetwork) { stopTest();latestGateway=null;latestInternet=null;message.value="当前上网网络已切换，原测试已停止，请重新检测" }
     if(previous.networkId!=s.networkId) { latestGateway=null;latestInternet=null }
     if(previous.bssid!=null && s.bssid!=previous.bssid) events.value=(events.value+"BSSID 切换：${previous.bssid} → ${s.bssid ?: "未知"}").takeLast(50)
     if(downloadNetwork!=null && downloadNetwork!=((s.networkId+":"+s.bssid) to s.addresses.sorted())) {
      downloadResult.value=null;downloadStatus.value="网络已变化，请重新测速";downloadNetwork=null;stopTest()
     }
     snapshot.value=s;samples.value=(samples.value+s).takeLast(300)
    } catch(e:SecurityException) { snapshot.value=WifiSnapshot(status="权限不足") }
    catch(e:Exception) { snapshot.value=WifiSnapshot(status="读取失败：${e.javaClass.simpleName}") }
    delay(interval.value)
   }
  }
 }
 fun pause() { paused.value=!paused.value;foreground(!paused.value) }
 fun setInterval(ms:Long) { viewModelScope.launch { settings.setInterval(ms) } }
 fun requestScan() {
  message.value=source.requestScan();scanTimeout?.cancel()
  if(message.value.startsWith("扫描请求")) scanTimeout=viewModelScope.launch {
   delay(15_000)
   if(message.value.startsWith("扫描请求")) message.value="等待扫描结果超时；继续显示缓存，请检查精确位置权限、定位服务、Wi-Fi开关或稍后重试"
  }
 }
 fun stopTest() { testing?.cancel();testing=null;testNetwork=null;geoJob?.cancel() }
 fun test(host:String,port:Int,gateway:Boolean=false,dnsOnly:Boolean=false,httpsOnly:Boolean=false) {
  if(busy.value) return
  val current=runCatching { source.read() }.getOrElse { message.value="无法读取当前网络：${it.message}";return }
  snapshot.value=current
  busy.value=true
  testNetwork=current.networkId
  testing=viewModelScope.launch {
   try {
    val started=java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",java.util.Locale.getDefault()).format(java.util.Date())
    val contextLabel="测试时间 $started · ${snapshot.value.transport} · SSID ${snapshot.value.ssid ?: "未知"} · BSSID ${snapshot.value.bssid ?: "未知"}\n"
    val actual=if(gateway) snapshot.value.gateway ?: error("没有可用的 IPv4 默认网关") else host.trim()
    require(actual.isNotBlank() && !actual.contains("://") && !actual.contains('/')) { "请输入主机名或 IP，不含 URL 路径" }
    if(gateway) {
     val now=System.nanoTime()/1_000_000
     require(lastGatewayCheck?.let { now-it>=30_000 } ?: true) { "网关检查间隔至少30秒，请稍后再试" }
     lastGatewayCheck=now
     require(snapshot.value.transport in listOf("Wi-Fi","以太网")) { "仅支持已授权的Wi-Fi/以太网私有网关；移动网络或VPN不检查路由器管理接口" }
     require(NetworkPolicy.privateGateway(actual)) { "当前网关不是可检查的私有IPv4地址，已跳过" }
     val icmp=tester.gatewayPing(actual)
     val ports=mutableListOf<String>()
     for(p in listOf(443,80)) { val probe=tester.tcp(actual,p,1).single();ports+="$p：${probe.outcome}${probe.millis?.let { " · ${fmt(it)} ms" } ?: ""}" }
     testResult.value=contextLabel+"已授权网关 $actual · 有限连通性检查\n$icmp\n${ports.joinToString("\n")}\n管理端口关闭不是断网或漏洞。未尝试登录、认证绕过或读取配置；请使用默认公网HTTPS测试进一步确认上网情况。"
    } else if(httpsOnly) {
     val response=tester.https(actual)
     testResult.value=contextLabel+"HTTPS HEAD https://$actual/（固定443，单次请求，不跟随跳转）\nHTTP ${response.code} · 总响应时间 ${fmt(response.totalMillis)} ms\n${NetworkPolicy.httpsMeaning(response.code)}\n该时间包含DNS、TCP、TLS及服务端响应，不是TCP延迟或带宽测速。"
    } else if(dnsOnly) {
     val (addresses,time)=tester.dns(actual)
     testResult.value=contextLabel+"系统 DNS（绑定当前默认上网网络，绕过本地查询缓存；上游仍可能缓存）\n${"%.2f".format(time)} ms\n${addresses.joinToString("\n")}\nA/AAAA 地址由系统合并返回；未测试指定 DNS 或 DoH。"
    } else {
     val probes=tester.tcp(actual,port);val stats=Analysis.stats(probes)
     if(gateway) latestGateway=stats else latestInternet=stats
     testResult.value=contextLabel+"目标 $actual:$port · TCP 建连（不含 DNS 时间）\n成功 ${stats.success}/${stats.count}；失败率 ${"%.0f".format(stats.failureRate*100)}%\n最小 ${fmt(stats.min)} / 最大 ${fmt(stats.max)} / 平均 ${fmt(stats.mean)} ms\n中位数 ${fmt(stats.median)} / P95 ${fmt(stats.p95)} / Jitter ${fmt(stats.jitter)} ms\n${probes.joinToString("\n") { it.outcome }}\n失败率不是 ICMP 丢包率。拒绝连接不代表离线；仅成功建连计入延迟。"
    }
   } catch(e:TimeoutCancellationException) { testResult.value="检测超时：目标、DNS或服务器未及时响应；不代表整个网络断开" }
   catch(e:CancellationException) { testResult.value="检测已取消";throw e }
   catch(e:Exception) { testResult.value="检测失败：${e.message}" }
   finally { busy.value=false;testNetwork=null }
  }
 }
 fun download(url:String,locationName:String,note:String,confirmedNetworkId:String?,confirmedMetered:Boolean) {
  if(busy.value) return
  val measuredSnapshot=runCatching { source.read() }.getOrElse { message.value="无法读取当前网络：${it.message}";return }
  if(measuredSnapshot.networkId==null || measuredSnapshot.networkId!=confirmedNetworkId || (measuredSnapshot.metered && !confirmedMetered)) {
   message.value="网络或计费状态已变化，请重新打开测速确认框";return
  }
  val measuredGeo=NetworkPolicy.freshGeo(geo.value,System.currentTimeMillis())
  val measuredName=locationName.trim().ifBlank { "未命名位置" };val measuredNote=note.trim()
  testNetwork=measuredSnapshot.networkId
  busy.value=true;downloadResult.value=null
  downloadNetwork=(measuredSnapshot.networkId+":"+measuredSnapshot.bssid) to measuredSnapshot.addresses.sorted()
  downloadStatus.value="正在连接测速服务器…"
  testing=viewModelScope.launch {
   try {
    val result=downloader.download(url.trim(),measuredSnapshot.networkId,confirmedMetered) { bytes,seconds ->
     downloadStatus.value="正在测速：${"%.1f".format(NetworkOverview.mbps(bytes,seconds))} Mbps · 已下载 ${"%.1f".format(bytes/1e6)} MB"
    }
    locationsDao.insert(LocationRecord(name=measuredName,note=measuredNote,time=result.time,ssid=measuredSnapshot.ssid,bssid=measuredSnapshot.bssid,rssi=measuredSnapshot.rssi,frequency=measuredSnapshot.frequency,mbps=result.mbps,bytes=result.bytes,seconds=result.seconds,server=result.server,networkType=measuredSnapshot.transport,latitude=measuredGeo?.latitude,longitude=measuredGeo?.longitude,accuracy=measuredGeo?.accuracy,geoTime=measuredGeo?.time,geoMock=measuredGeo?.mock))
    message.value="已保存位置：$measuredName（信号为测速开始时采样）"
    downloadResult.value=result
    downloadStatus.value="位置：$measuredName\n本次参考：${"%.1f".format(result.mbps)} Mbps（${"%.1f".format(result.mbps/8)} MB/s）\n${"%.1f".format(result.seconds)} 秒 · 下载 ${"%.1f".format(result.bytes/1e6)} MB\n测量时间 ${java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.getDefault()).format(java.util.Date(result.time))}\n服务器 ${result.server}"
    testResult.value="HTTPS 下载参考测速\n${downloadStatus.value}\n${NetworkOverview.speed(result)}\n单连接平均速度，包含建连开销，不等于宽带套餐峰值；未测上传速度。"
   } catch(e:TimeoutCancellationException) { downloadResult.value=null;downloadStatus.value="测速超过18秒上限，已停止；请更换服务器或稍后再试";testResult.value=downloadStatus.value }
   catch(e:CancellationException) { downloadResult.value=null;downloadStatus.value="测速已停止，未给出最终速度";testResult.value=downloadStatus.value;throw e }
   catch(e:Exception) { downloadResult.value=null;downloadStatus.value="测速失败：${e.message}。不能据此认定无法上网。";testResult.value=downloadStatus.value }
   finally { busy.value=false;testNetwork=null }
  }
 }
 fun saveLocation(name:String,note:String) { viewModelScope.launch {
  try {
   val s=source.read()
   require(s.networkId!=null) { "当前未连接网络；仍可手动填写位置，联网后再记录" }
   val measuredGeo=NetworkPolicy.freshGeo(geo.value,System.currentTimeMillis())
   locationsDao.insert(LocationRecord(name=name.trim().ifBlank { "未命名位置" },note=note.trim(),time=s.time,ssid=s.ssid,bssid=s.bssid,rssi=s.rssi,frequency=s.frequency,mbps=null,bytes=null,seconds=null,server=null,networkType=s.transport,latitude=measuredGeo?.latitude,longitude=measuredGeo?.longitude,accuracy=measuredGeo?.accuracy,geoTime=measuredGeo?.time,geoMock=measuredGeo?.mock))
   message.value="位置与当前网络状态已记录（未测速；缺失信号不填数值）"
  } catch(e:Exception) { message.value="记录失败：${e.message}" }
 } }
 fun locate() {
  if(locating.value) return
  locating.value=true;geo.value=null;geoStatus.value="正在获取系统定位（最多15秒）…"
  geoJob=viewModelScope.launch {
   try { geo.value=locator.current();geoStatus.value="定位已获取，可附加到2分钟内的新记录；请核对精度和采集时间" }
   catch(e:TimeoutCancellationException) { geoStatus.value="定位超时；室内可能无卫星信号，可改用手动房间名称" }
   catch(e:CancellationException) { geoStatus.value="定位已停止";throw e }
   catch(e:Exception) { geoStatus.value="无法定位：${e.message}" }
   finally { locating.value=false }
  }
 }
 fun removeGeo() { geoJob?.cancel();geo.value=null;geoStatus.value="已移除待附加坐标；历史记录需清除历史才能删除" }
 fun renameLocation(id:Long,name:String,note:String) { viewModelScope.launch {
  try { require(name.isNotBlank()) { "位置名称不能为空" };locationsDao.rename(id,name.trim(),note.trim());message.value="位置名称和备注已更新" }
  catch(e:Exception) { message.value="修改失败：${e.message}" }
 } }
 fun save() { viewModelScope.launch {
  try { val s=snapshot.value;dao.insert(Session(time=s.time,ssid=s.ssid,bssid=s.bssid,rssi=s.rssi,frequency=s.frequency,detail="${s.status}\nIP ${s.addresses}\n网关 ${s.gateway}\nDNS ${s.dns}\n${testResult.value}\n${Analysis.advice(s).joinToString("\n")}"));message.value="已保存至本机历史" }
  catch(e:Exception) { message.value="保存失败：${e.message}" }
 } }
 fun clear() { viewModelScope.launch { try { dao.clear();locationsDao.clear();removeGeo();message.value="历史已清除" } catch(e:Exception) { message.value="清除失败" } } }
 fun gatewaySummary()="请在网络检测页执行已授权网关的有限检查"
 fun internetSummary()=latestInternet?.let { "${fmt(it.mean)} ms（成功 ${it.success}/${it.count}）" } ?: "尚未检测"
 companion object { fun fmt(n:Double?)=n?.let { "%.2f".format(it) } ?: "无法获取" }
}
