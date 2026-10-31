package com.homewifi.inspector.data
import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.*
import android.net.wifi.*
import android.os.Build
import android.os.SystemClock
import com.homewifi.inspector.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

@Suppress("DEPRECATION")
class AndroidWifiSource @Inject constructor(@ApplicationContext private val context: Context) : WifiSource {
 private val cm=context.getSystemService(ConnectivityManager::class.java)
 private val wm=context.getSystemService(WifiManager::class.java)
 @Volatile private var latestCaps:Pair<Network,NetworkCapabilities>?=null
 private var lastRequest: Long? = null
 private fun permitted()=context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
 // Platform constants below are compile-time inline values, safe on API 29.
 @android.annotation.SuppressLint("InlinedApi")
 override fun read(): WifiSnapshot {
  val network=cm.activeNetwork ?: return WifiSnapshot(status=if(wm.isWifiEnabled) "没有默认上网网络；请连接 Wi-Fi 或移动网络" else "Wi-Fi 已关闭；当前没有其他上网网络")
  val caps=latestCaps?.takeIf { it.first==network }?.second ?: cm.getNetworkCapabilities(network) ?: return WifiSnapshot(status="网络状态暂不可用")
  val links=cm.getLinkProperties(network)
  val transport=ActiveNetworkProvider.transport(caps)
  val common=WifiSnapshot(networkId=network.toString(),transport=transport,metered=!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),status="当前使用$transport",addresses=links?.linkAddresses?.map { it.toString() }.orEmpty(),gateway=links?.routes?.firstOrNull { it.isDefaultRoute && it.gateway is java.net.Inet4Address }?.gateway?.hostAddress,dns=links?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty(),validated=caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),internet=caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),captive=caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),privateDns=links?.let { if(it.isPrivateDnsActive) it.privateDnsServerName ?: "Private DNS 活跃，服务名不可见" else "系统未报告 Private DNS 活跃" })
  if(transport!="Wi-Fi") return common
  val info=(caps.transportInfo as? WifiInfo)?.takeIf { it.rssi != -127 && it.ssid != WifiManager.UNKNOWN_SSID } ?: wm.connectionInfo
  val allowed=permitted() && context.getSystemService(LocationManager::class.java).isLocationEnabled
  return common.copy(status=if(allowed) "已连接 Wi-Fi" else "已连接 Wi-Fi；读取标识和扫描需要精确位置权限及开启定位服务",
   ssid=info.ssid?.trim('"')?.takeUnless { it==WifiManager.UNKNOWN_SSID }?.takeIf { allowed },
   bssid=info.bssid?.takeUnless { it=="02:00:00:00:00:00" }?.takeIf { allowed }, rssi=info.rssi.takeIf { it in -126..-1 },
   frequency=info.frequency.takeIf { it>0 },tx=info.txLinkSpeedMbps.takeIf { it>=0 },rx=info.rxLinkSpeedMbps.takeIf { it>=0 },
   standard=if(Build.VERSION.SDK_INT>=30) when(info.wifiStandard) { ScanResult.WIFI_STANDARD_11N->"802.11n";ScanResult.WIFI_STANDARD_11AC->"802.11ac";ScanResult.WIFI_STANDARD_11AX->"802.11ax";ScanResult.WIFI_STANDARD_11AD->"802.11ad";ScanResult.WIFI_STANDARD_11BE->"802.11be";ScanResult.WIFI_STANDARD_LEGACY->"Legacy";else->null } else null,
   security=if(Build.VERSION.SDK_INT>=31) when(info.currentSecurityType) { WifiInfo.SECURITY_TYPE_OPEN->"Open";WifiInfo.SECURITY_TYPE_WEP->"WEP";WifiInfo.SECURITY_TYPE_PSK->"Personal PSK（WPA/WPA2，API 未区分）";WifiInfo.SECURITY_TYPE_EAP->"Enterprise";WifiInfo.SECURITY_TYPE_SAE->"WPA3-Personal";WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT->"WPA3-Enterprise 192-bit";WifiInfo.SECURITY_TYPE_OWE->"OWE";WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE->"WPA3-Enterprise";else->null } else null,
   addresses=links?.linkAddresses?.map { it.toString() }.orEmpty(),
   gateway=links?.routes?.firstOrNull { it.isDefaultRoute && it.gateway is java.net.Inet4Address }?.gateway?.hostAddress,
   dns=links?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty(),
   validated=caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
   internet=caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),captive=caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
   privateDns=links?.let { if(it.isPrivateDnsActive) it.privateDnsServerName ?: "Private DNS 活跃，服务名不可见" else "系统未报告 Private DNS 活跃" },
   mlo=if(Build.VERSION.SDK_INT>=34) info.apMloLinkId.takeIf { it>=0 }?.let { "AP link ID: $it" } else null)
 }
 @android.annotation.SuppressLint("InlinedApi")
 override fun changes()=callbackFlow {
  var lastState:String?=null
  fun available(network:Network) { trySend("默认上网网络可用：$network") }
  fun lost(network:Network) { if(latestCaps?.first==network) latestCaps=null;lastState=null;trySend("默认上网网络断开：$network") }
  fun capabilities(network:Network,caps:NetworkCapabilities) {
   latestCaps=network to caps
   val state="$network · ${ActiveNetworkProvider.transport(caps)} · 系统验证=${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)} · 认证门户=${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)} · 计费=${!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)}"
   if(state!=lastState) { lastState=state;trySend("网络状态变化：$state") }
  }
  val callback=if(Build.VERSION.SDK_INT>=31) object:ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
   override fun onAvailable(network:Network)=available(network)
   override fun onLost(network:Network)=lost(network)
   override fun onCapabilitiesChanged(network:Network,caps:NetworkCapabilities)=capabilities(network,caps)
  } else object:ConnectivityManager.NetworkCallback() {
   override fun onAvailable(network:Network)=available(network)
   override fun onLost(network:Network)=lost(network)
   override fun onCapabilitiesChanged(network:Network,caps:NetworkCapabilities)=capabilities(network,caps)
  }
  cm.registerDefaultNetworkCallback(callback)
  awaitClose { cm.unregisterNetworkCallback(callback) }
 }
 private fun cached(message:String):ScanState = try {
  ScanState(message,wm.scanResults.map { AccessPoint(it.SSID.ifBlank { "隐藏 SSID" },it.BSSID,it.level,it.frequency,it.channelWidth,it.centerFreq0,it.capabilities,it.timestamp) }.sortedByDescending { it.rssi })
 } catch(e:SecurityException) { ScanState("需要精确位置权限及开启定位服务") }
 override fun scans()=callbackFlow {
  val receiver=object:BroadcastReceiver() { override fun onReceive(c:Context,i:Intent) {
   trySend(cached(if(i.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED,false)) "系统扫描结果已更新" else "系统未更新结果：可能限频、空闲状态或硬件失败；显示缓存"))
  } }
  if(Build.VERSION.SDK_INT>=33) context.registerReceiver(receiver,IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),Context.RECEIVER_EXPORTED)
  else context.registerReceiver(receiver,IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
  trySend(cached("系统缓存；时间见各 AP"))
  awaitClose { context.unregisterReceiver(receiver) }
 }
 override fun requestScan():String {
  if(!permitted()) return "需要精确位置权限"
  if(!context.getSystemService(LocationManager::class.java).isLocationEnabled) return "需要开启系统定位服务"
  if(!wm.isWifiEnabled) return "Wi-Fi 已关闭"
  val now=SystemClock.elapsedRealtime()
  if(lastRequest?.let { now-it<30_000 }==true) return "本应用限制：扫描间隔至少 30 秒，继续显示缓存"
  lastRequest=now
  return try { if(wm.startScan()) "扫描请求已提交，等待系统广播" else "系统拒绝扫描：可能限频、空闲或硬件失败，继续显示缓存" } catch(e:SecurityException) { "扫描权限不足" }
 }
}
