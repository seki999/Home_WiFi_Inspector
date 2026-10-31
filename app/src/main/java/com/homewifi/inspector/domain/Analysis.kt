package com.homewifi.inspector.domain
import kotlin.math.*
object Analysis {
 fun band(mhz: Int) = when(mhz) { in 2400..2500 -> "2.4 GHz"; in 4900..5895 -> "5 GHz"; in 5925..7125 -> "6 GHz"; else -> "未知" }
 fun channel(mhz: Int): Int? = when {
  mhz == 2484 -> 14
  mhz in 2412..2472 && (mhz-2407)%5 == 0 -> (mhz-2407)/5
  mhz == 5935 -> 2
  mhz in 5955..7115 && (mhz-5950)%5 == 0 -> (mhz-5950)/5
  mhz in 5000..5895 && (mhz-5000)%5 == 0 -> (mhz-5000)/5
  mhz in 4910..4980 && (mhz-4000)%5 == 0 -> (mhz-4000)/5
  else -> null
 }
 fun grade(rssi: Int) = when { rssi >= -50 -> "非常强"; rssi >= -60 -> "强"; rssi >= -67 -> "良好"; rssi >= -75 -> "一般"; rssi >= -85 -> "弱"; else -> "非常弱" }
 // Missing factors do not receive values: normalize weights over measured components only.
 fun score(rssi: Int?, latency: Double? = null, failure: Double? = null, fluctuation: Double? = null): Int? {
  val parts = listOfNotNull(rssi?.let { ((it+90)/40.0*100).coerceIn(0.0,100.0) to .55 },
   latency?.let { (100-it*2).coerceIn(0.0,100.0) to .2 },
   failure?.let { (100-it*100).coerceIn(0.0,100.0) to .15 },
   fluctuation?.let { (100-it*5).coerceIn(0.0,100.0) to .1 })
  return if(parts.isEmpty()) null else (parts.sumOf { it.first*it.second }/parts.sumOf { it.second }).roundToInt()
 }
 fun stats(probes: List<Probe>): LatencyStats {
  val ok=probes.mapNotNull { it.millis }; val sorted=ok.sorted()
  val differences=probes.zipWithNext().mapNotNull { (a,b) -> if(a.millis!=null && b.millis!=null) abs(a.millis-b.millis) else null }
  return LatencyStats(probes.size,ok.size,sorted.firstOrNull(),sorted.lastOrNull(),ok.takeIf { it.isNotEmpty() }?.average(),
   if(sorted.isEmpty()) null else if(sorted.size%2==0) (sorted[sorted.size/2-1]+sorted[sorted.size/2])/2 else sorted[sorted.size/2],
   sorted.getOrNull(ceil(sorted.size*.95).toInt()-1),differences.takeIf { it.isNotEmpty() }?.average(),
   if(probes.isEmpty()) 0.0 else (probes.size-ok.size).toDouble()/probes.size)
 }
 fun widthMhz(code: Int) = when(code) { 0 -> 20; 1 -> 40; 2 -> 80; 3 -> 160; 5 -> 320; else -> null }
 fun congestion(frequency: Int, width: Int, aps: List<AccessPoint>): Double = aps.filter { band(it.frequency)==band(frequency) }.sumOf {
  val apWidth=widthMhz(it.width) ?: 20
  val overlap=(min(frequency+width/2.0,(if(it.center>0) it.center else it.frequency)+apWidth/2.0)-max(frequency-width/2.0,(if(it.center>0) it.center else it.frequency)-apWidth/2.0)).coerceAtLeast(0.0)/width
  overlap * 10.0.pow((it.rssi+90)/20.0)
 }
 fun advice(s: WifiSnapshot): List<String> = buildList {
  if(s.rssi!=null && s.rssi < -75) add("当前采样信号偏弱；移动至路由器附近比较，并持续采样确认。单次采样置信度低。")
  if(s.captive) add("系统检测到认证门户，请先完成网络登录。")
  if(!s.validated) add("Android 尚未验证互联网可用性；需结合 DNS 和外部目标测试确认。")
  if(s.security=="Open" || s.security=="WEP") add("无线加密风险：${s.security}，建议改用 WPA2-AES 或 WPA3。")
 }
}
