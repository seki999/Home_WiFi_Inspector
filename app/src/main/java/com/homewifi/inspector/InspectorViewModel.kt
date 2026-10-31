package com.homewifi.inspector
import androidx.lifecycle.*
import com.homewifi.inspector.domain.*
import com.homewifi.inspector.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@HiltViewModel
class InspectorViewModel @Inject constructor(private val source:WifiSource,private val tester:NetworkTester,private val dao:SessionDao,private val settings:Settings):ViewModel() {
 val snapshot=MutableStateFlow(WifiSnapshot())
 val samples=MutableStateFlow<List<WifiSnapshot>>(emptyList())
 val scan=MutableStateFlow(ScanState())
 val events=MutableStateFlow<List<String>>(emptyList())
 val message=MutableStateFlow("")
 val testResult=MutableStateFlow("尚未检测")
 val busy=MutableStateFlow(false)
 val paused=MutableStateFlow(false)
 val interval=settings.intervalMs.stateIn(viewModelScope,SharingStarted.Eagerly,2000L)
 val history=dao.observe().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 private var monitor:Job?=null
 private var testing:Job?=null
 private var latestGateway:LatencyStats?=null
 private var latestInternet:LatencyStats?=null
 fun foreground(active:Boolean) {
  monitor?.cancel();monitor=null
  if(!active || paused.value) return
  monitor=viewModelScope.launch {
   launch { source.changes().catch { message.value="网络回调不可用：${it.message}" }.collect { events.value=(events.value+it).takeLast(50) } }
   launch { source.scans().catch { message.value="扫描监听失败：${it.message}" }.collect { scan.value=it } }
   while(isActive) {
    try {
     val s=source.read();val previous=snapshot.value
     if(previous.bssid!=null && s.bssid!=previous.bssid) events.value=(events.value+"BSSID 切换：${previous.bssid} → ${s.bssid ?: "未知"}").takeLast(50)
     snapshot.value=s;samples.value=(samples.value+s).takeLast(300)
    } catch(e:SecurityException) { snapshot.value=WifiSnapshot(status="权限不足") }
    catch(e:Exception) { snapshot.value=WifiSnapshot(status="读取失败：${e.javaClass.simpleName}") }
    delay(interval.value)
   }
  }
 }
 fun pause() { paused.value=!paused.value;foreground(!paused.value) }
 fun setInterval(ms:Long) { viewModelScope.launch { settings.setInterval(ms) } }
 fun requestScan() { message.value=source.requestScan() }
 fun stopTest() { testing?.cancel();testing=null }
 fun test(host:String,port:Int,gateway:Boolean=false,dnsOnly:Boolean=false) {
  if(busy.value) return
  busy.value=true
  testing=viewModelScope.launch {
   try {
    val started=java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",java.util.Locale.getDefault()).format(java.util.Date())
    val contextLabel="测试时间 $started · SSID ${snapshot.value.ssid ?: "未知"} · BSSID ${snapshot.value.bssid ?: "未知"}\n"
    val actual=if(gateway) snapshot.value.gateway ?: error("没有可用的 IPv4 默认网关") else host.trim()
    require(actual.isNotBlank() && !actual.contains("://") && !actual.contains('/')) { "请输入主机名或 IP，不含 URL 路径" }
    if(dnsOnly) {
     val (addresses,time)=tester.dns(actual)
     testResult.value=contextLabel+"系统 DNS（绑定 Wi-Fi，绕过本地查询缓存；上游仍可能缓存）\n${"%.2f".format(time)} ms\n${addresses.joinToString("\n")}\nA/AAAA 地址由系统合并返回；未测试指定 DNS 或 DoH。"
    } else {
     val probes=tester.tcp(actual,port);val stats=Analysis.stats(probes)
     if(gateway) latestGateway=stats else latestInternet=stats
     testResult.value=contextLabel+"目标 $actual:$port · TCP 建连（不含 DNS 时间）\n成功 ${stats.success}/${stats.count}；失败率 ${"%.0f".format(stats.failureRate*100)}%\n最小 ${fmt(stats.min)} / 最大 ${fmt(stats.max)} / 平均 ${fmt(stats.mean)} ms\n中位数 ${fmt(stats.median)} / P95 ${fmt(stats.p95)} / Jitter ${fmt(stats.jitter)} ms\n${probes.joinToString("\n") { it.outcome }}\n失败率不是 ICMP 丢包率。拒绝连接不代表离线；仅成功建连计入延迟。"
    }
   } catch(e:CancellationException) { testResult.value="检测已取消";throw e }
   catch(e:Exception) { testResult.value="检测失败：${e.message}" }
   finally { busy.value=false }
  }
 }
 fun save() { viewModelScope.launch {
  try { val s=snapshot.value;dao.insert(Session(time=s.time,ssid=s.ssid,bssid=s.bssid,rssi=s.rssi,frequency=s.frequency,detail="${s.status}\nIP ${s.addresses}\n网关 ${s.gateway}\nDNS ${s.dns}\n${testResult.value}\n${Analysis.advice(s).joinToString("\n")}"));message.value="已保存至本机历史" }
  catch(e:Exception) { message.value="保存失败：${e.message}" }
 } }
 fun clear() { viewModelScope.launch { try { dao.clear();message.value="历史已清除" } catch(e:Exception) { message.value="清除失败" } } }
 fun gatewaySummary()=latestGateway?.let { "${fmt(it.mean)} ms（成功 ${it.success}/${it.count}）" } ?: "尚未检测"
 fun internetSummary()=latestInternet?.let { "${fmt(it.mean)} ms（成功 ${it.success}/${it.count}）" } ?: "尚未检测"
 companion object { fun fmt(n:Double?)=n?.let { "%.2f".format(it) } ?: "无法获取" }
}
