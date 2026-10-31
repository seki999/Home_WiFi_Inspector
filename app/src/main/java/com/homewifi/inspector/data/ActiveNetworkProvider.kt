package com.homewifi.inspector.data
import android.content.Context
import android.net.*
import com.homewifi.inspector.domain.NetworkPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
class ActiveNetworkProvider @Inject constructor(@ApplicationContext context:Context) {
 private val cm=context.getSystemService(ConnectivityManager::class.java)
 fun current():Network=cm.activeNetwork ?: error("当前没有可用的默认网络，请先连接 Wi-Fi 或移动网络")
 fun requireCurrent(network:Network,allowMetered:Boolean=true) {
  check(cm.activeNetwork==network) { "当前上网网络已切换，请重新检测" }
  if(!allowMetered) check(cm.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)==true) { "网络计费状态发生变化，已停止；请确认流量后重测" }
 }
 companion object {
  fun transport(caps:NetworkCapabilities)=NetworkPolicy.transport(caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
 }
}
