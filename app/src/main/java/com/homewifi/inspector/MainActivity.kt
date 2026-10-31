package com.homewifi.inspector
import android.Manifest
import android.os.*
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import com.homewifi.inspector.domain.*
import com.homewifi.inspector.data.Session
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.*
import org.json.*

@AndroidEntryPoint
class MainActivity:ComponentActivity() {
 private val vm:InspectorViewModel by viewModels()
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  enableEdgeToEdge()
  setContent { MaterialTheme(colorScheme=if(isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) { Surface(Modifier.fillMaxSize()) { Inspector(vm) } } }
 }
 @Composable private fun Inspector(vm:InspectorViewModel) {
  val lifecycle=LocalLifecycleOwner.current.lifecycle
  DisposableEffect(lifecycle) {
   val observer=LifecycleEventObserver { _,event ->
    if(event==Lifecycle.Event.ON_START) vm.foreground(true)
    if(event==Lifecycle.Event.ON_STOP) { vm.foreground(false);vm.stopTest() }
   }
   lifecycle.addObserver(observer)
   if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) vm.foreground(true)
   onDispose { lifecycle.removeObserver(observer);vm.foreground(false) }
  }
  val s by vm.snapshot.collectAsStateWithLifecycle()
  val samples by vm.samples.collectAsStateWithLifecycle()
  val scan by vm.scan.collectAsStateWithLifecycle()
  val events by vm.events.collectAsStateWithLifecycle()
  val message by vm.message.collectAsStateWithLifecycle()
  val result by vm.testResult.collectAsStateWithLifecycle()
  val busy by vm.busy.collectAsStateWithLifecycle()
  val paused by vm.paused.collectAsStateWithLifecycle()
  val interval by vm.interval.collectAsStateWithLifecycle()
  val history by vm.history.collectAsStateWithLifecycle()
  var tab by rememberSaveable { mutableIntStateOf(0) }
  var host by rememberSaveable { mutableStateOf("") }
  var port by rememberSaveable { mutableStateOf("443") }
  var authorized by rememberSaveable { mutableStateOf(false) }
  var redact by rememberSaveable { mutableStateOf(true) }
  var exportFormat by remember { mutableStateOf("json") }
  var clearDialog by remember { mutableStateOf(false) }
  val scope=rememberCoroutineScope()
  val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.foreground(true) }
  val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
   if(uri!=null) scope.launch {
    try { withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(exportReport(history,redact,exportFormat)) } ?: error("无法打开导出文件") };vm.message.value="导出成功" }
    catch(e:Exception) { vm.message.value="导出失败：${e.message}" }
   }
  }
  Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
   Text("Home WiFi Inspector",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(16.dp))
   ScrollableTabRow(selectedTabIndex=tab,edgePadding=0.dp) { listOf("首页","Wi-Fi 分析","网络检测","历史","设置").forEachIndexed { i,label -> Tab(selected=tab==i,onClick={tab=i},text={Text(label)}) } }
   LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
    if(message.isNotBlank()) item { Text(message,color=MaterialTheme.colorScheme.primary) }
    when(tab) {
     0 -> {
      item { Panel("当前网络") {
       Text(s.ssid ?: "SSID 无法获取",style=MaterialTheme.typography.headlineSmall)
       Text(s.status)
       Text("${s.rssi?.let { "$it dBm · ${Analysis.grade(it)}" } ?: "信号无法获取"}")
       Text("信号参考分 ${Analysis.score(s.rssi)?.toString() ?: "—"}/100 · 仅 RSSI，已测 ${if(s.rssi==null) 0 else 1}/4 因素")
       Text("${s.frequency?.let { Analysis.band(it) } ?: "频段无法获取"} · 信道 ${s.frequency?.let { Analysis.channel(it) } ?: "—"}")
       Text("Tx ${s.tx ?: "—"} / Rx ${s.rx ?: "—"} Mbps（Wi-Fi 链路速率）")
       Text("互联网能力 ${s.internet} · 系统验证 ${s.validated} · 认证门户 ${s.captive}")
       Text("最近网关：${vm.gatewaySummary()}\n最近外部目标：${vm.internetSummary()}")
       Text("安全评估：尚未执行完整审计")
       Button(onClick={tab=2}) { Text("立即检测") }
      } }
      item { Panel("实时信号 · 最近 ${samples.size} 个采样") { Trend(samples.map { it.rssi }); Text("${interval/1000} 秒读取当前连接；不会主动扫描附近 AP")
       Row { OutlinedButton(onClick={vm.pause()}) { Text(if(paused) "继续" else "暂停") }; Spacer(Modifier.width(8.dp));Button(onClick={vm.save()}) { Text("保存记录") } }
      } }
      item { Panel("本地诊断建议") { val advice=Analysis.advice(s);if(advice.isEmpty()) Text("当前证据不足以判断网络瓶颈；请进行性能测试。") else advice.forEach { Text("• $it") } } }
     }
     1 -> {
      item { Panel("当前连接详情") {
       Text("SSID ${s.ssid ?: "权限不足或无法获取"}\nBSSID ${s.bssid ?: "权限不足或无法获取"}\n频率 ${s.frequency ?: "—"} MHz\n标准 ${s.standard ?: "设备不支持或无法获取"}\n安全 ${s.security ?: "系统不支持或未知"}\n地址 / 前缀 ${s.addresses.joinToString().ifBlank { "无法获取" }}\n默认网关 ${s.gateway ?: "无法获取 IPv4 网关"}\nDNS ${s.dns.joinToString().ifBlank { "无法获取" }}\nPrivate DNS ${s.privateDns ?: "无法获取"}\nMLO ${s.mlo ?: "设备不支持或无法获取"}")
       Text("当前信道宽度：请与扫描结果中的 BSSID 匹配查看；DHCP 租期等详情当前未提供。")
      } }
      item { Panel("附近 AP · 系统扫描") { Text(scan.message);Text("至少间隔 30 秒手动请求，Android 仍可能限频；旧结果按真实年龄显示。")
       Button(onClick={vm.requestScan()}) { Text("请求扫描") }
      } }
      item { Panel("信道分布 · ${scan.points.size} 个 AP") {
       listOf("2.4 GHz","5 GHz","6 GHz").forEach { band ->
        val points=scan.points.filter { Analysis.band(it.frequency)==band }
        Text("$band · ${points.size} AP")
        ChannelChart(points)
       }
       Text("柱高为 RSSI；拥挤指数根据可见 AP、RSSI、频宽重叠估计，无单位，不是空口占用率或噪声测量。80+80 MHz 未纳入准确建模。")
       s.frequency?.let { Text("当前主信道 20 MHz 参考拥挤指数：${"%.1f".format(Analysis.congestion(it,20,scan.points.filter { p -> p.bssid!=s.bssid }))}；缓存和隐藏 AP 会降低置信度。") }
      } }
      scan.points.groupBy { it.ssid }.forEach { (ssid,aps) ->
       item { Text("$ssid · ${aps.size} AP",style=MaterialTheme.typography.titleMedium) }
       items(aps,key={it.bssid}) { ap -> Panel(ap.bssid) {
        Text("${ap.rssi} dBm · ${Analysis.band(ap.frequency)} · 信道 ${Analysis.channel(ap.frequency) ?: "未知"} · ${Analysis.widthMhz(ap.width)?.let { "$it MHz" } ?: "80+80 或未知频宽"}")
        Text(ap.capabilities)
        Text("扫描年龄 ${((SystemClock.elapsedRealtime()*1000-ap.seenMicros)/1_000_000).coerceAtLeast(0)} 秒")
       } }
      }
      item { Panel("网络事件 · 本次前台会话") { events.takeLast(15).forEach { Text(it) };if(events.isEmpty()) Text("暂无事件") } }
     }
     2 -> {
      item { Panel("授权与测试目标") {
       Text("仅测试自己或明确授权的网络与目标。外部测试会向所选服务器发送少量 DNS/TCP 流量。此版本不提供带宽测速。")
       Row { Checkbox(authorized,{authorized=it});Text("我已确认拥有目标网络的测试授权") }
       OutlinedTextField(host,{host=it},label={Text("测试主机名 / IP（不含协议）")},singleLine=true,modifier=Modifier.fillMaxWidth())
       OutlinedTextField(port,{port=it},label={Text("TCP 端口")},singleLine=true)
       Button(enabled=authorized&&!busy&&port.toIntOrNull() in 1..65535,onClick={vm.test(host,port.toInt())}) { Text("测试目标 TCP · 5 次") }
       Button(enabled=authorized&&!busy&&port.toIntOrNull() in 1..65535,onClick={vm.test("",port.toInt(),gateway=true)}) { Text("测试默认网关 TCP · 5 次") }
       Button(enabled=authorized&&!busy&&host.isNotBlank(),onClick={vm.test(host,443,dnsOnly=true)}) { Text("系统 DNS 诊断") }
       if(busy) { LinearProgressIndicator(Modifier.fillMaxWidth());OutlinedButton(onClick={vm.stopTest()}) { Text("取消测试") } }
      } }
      item { Panel("检测结果") { SelectionContainerCompat(result);Button(onClick={vm.save()}) { Text("保存当前检测") } } }
     }
     3 -> {
      item { Panel("本机历史 · 最近 200 条") {
       Row { Checkbox(redact,{redact=it});Text("导出隐藏 SSID / BSSID / IP / 目标信息") }
       Row { Button(onClick={exportFormat="json";export.launch("wifi-report.json")}) { Text("JSON") };Spacer(Modifier.width(8.dp));Button(onClick={exportFormat="csv";export.launch("wifi-report.csv")}) { Text("CSV") } }
       OutlinedButton(onClick={clearDialog=true}) { Text("清除历史") }
       Text("保存的是手动记录快照和最近检测文本；连续样本与 AP 缓存未写入数据库。")
      } }
      items(history,key={it.id}) { record -> Panel(date(record.time)) { Text("${record.ssid ?: "SSID 未知"} · ${record.rssi ?: "—"} dBm · ${record.frequency ?: "—"} MHz"); Text(record.detail) } }
     }
     4 -> {
      item { Panel("权限与系统状态") {
       Text("Wi-Fi 扫描需要精确位置权限并开启定位服务。拒绝权限后仍可查看非敏感网络状态与历史。")
       Button(onClick={
        val list=mutableListOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION)
        if(Build.VERSION.SDK_INT>=33) list.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        if(Build.VERSION.SDK_INT>=37) list.add("android.permission.ACCESS_LOCAL_NETWORK")
        permissions.launch(list.toTypedArray())
       }) { Text("授予检测权限") }
       OutlinedButton(onClick={startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))}) { Text("打开定位设置") }
       OutlinedButton(onClick={startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:$packageName")))}) { Text("应用权限设置") }
      } }
      item { Panel("采样间隔") { Row { listOf(1000L,2000L,5000L).forEach { ms -> FilterChip(selected=interval==ms,onClick={vm.setInterval(ms)},label={Text("${ms/1000} 秒")});Spacer(Modifier.width(8.dp)) } };Text("仅在界面前台采样；后台停止监控与主动测试。最多保留 300 个内存采样。") } }
      item { Panel("版本与隐私") { Text("0.1.0 · Android 10+\n本地存储，无账号、无分析 SDK、不上传扫描结果。主题跟随系统。完整安全审计、设备发现、热力图、PDF 和完整多语言界面尚未实现，详情见项目文档。") } }
     }
    }
   }
  }
  if(clearDialog) AlertDialog(onDismissRequest={clearDialog=false},title={Text("清除全部历史？")},text={Text("删除本机保存的所有快照，无法恢复。")},confirmButton={TextButton(onClick={vm.clear();clearDialog=false}) { Text("清除") }},dismissButton={TextButton(onClick={clearDialog=false}) { Text("取消") }})
 }
}
@Composable private fun Panel(title:String,content:@Composable ColumnScope.()->Unit) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) { Text(title,style=MaterialTheme.typography.titleMedium);content() } } }
@Composable private fun SelectionContainerCompat(text:String) { androidx.compose.foundation.text.selection.SelectionContainer { Text(text) } }
@Composable private fun Trend(values:List<Int?>) {
 val color=MaterialTheme.colorScheme.primary
 Canvas(Modifier.fillMaxWidth().height(130.dp)) {
  listOf(-50,-70,-90).forEach { value -> val y=((-value-30)/70f)*size.height;drawLine(Color.Gray.copy(alpha=.3f),Offset(0f,y),Offset(size.width,y)) }
  values.zipWithNext().forEachIndexed { index,(a,b) -> if(a!=null&&b!=null) {
   fun y(v:Int)=((-v-30)/70f).coerceIn(0f,1f)*size.height
   val divisor=(values.size-1).coerceAtLeast(1)
   drawLine(color,Offset(index.toFloat()/divisor*size.width,y(a)),Offset((index+1f)/divisor*size.width,y(b)),3f)
  } }
 }
 Text("纵轴 −30 至 −100 dBm；横轴为采样顺序，断连处留空。")
}
@Composable private fun ChannelChart(points:List<AccessPoint>) {
 val color=MaterialTheme.colorScheme.primary
 if(points.isEmpty()) { Text("无扫描数据");return }
 val min=points.minOf { it.frequency }-20;val max=points.maxOf { it.frequency }+20
 Canvas(Modifier.fillMaxWidth().height(70.dp)) { points.forEach { ap ->
  val x=(ap.frequency-min).toFloat()/(max-min)*size.width
  val h=((ap.rssi+100)/70f).coerceIn(0f,1f)*size.height
  drawLine(color.copy(alpha=.6f),Offset(x,size.height),Offset(x,size.height-h),6f)
 } }
 Text("${Analysis.channel(min+20) ?: "?"} — ${Analysis.channel(max-20) ?: "?"} 信道")
}
private fun date(time:Long)=SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(Date(time))
internal fun exportReport(rows:List<Session>,redact:Boolean,format:String):String {
 if(format=="csv") {
  fun quote(value:Any?):String { val s=value?.toString().orEmpty();val safe=if(s.firstOrNull() in listOf('=','+','-','@','\t','\r')) "'"+s else s;return "\""+safe.replace("\"","\"\"")+"\"" }
  return "time,ssid,bssid,rssi_dbm,frequency_mhz,detail\r\n"+rows.joinToString("\r\n") { listOf(it.time,if(redact) "已隐藏" else it.ssid,if(redact) "已隐藏" else it.bssid,it.rssi,it.frequency,if(redact) "含网络标识的详情已隐藏" else it.detail).joinToString(",") { v -> quote(v) } }
 }
 return JSONObject().put("schemaVersion",1).put("redacted",redact).put("device",if(redact) "已隐藏" else "${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}").put("records",JSONArray().apply { rows.forEach { row -> put(JSONObject().put("time",row.time).put("ssid",if(redact) "已隐藏" else row.ssid ?: JSONObject.NULL).put("bssid",if(redact) "已隐藏" else row.bssid ?: JSONObject.NULL).put("rssi",row.rssi ?: JSONObject.NULL).put("frequency",row.frequency ?: JSONObject.NULL).put("detail",if(redact) "含网络标识的详情已隐藏" else row.detail)) } }).toString(2)
}
