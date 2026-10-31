package com.homewifi.inspector.domain

data class DownloadResult(val mbps:Double, val bytes:Long, val seconds:Double, val server:String, val time:Long=System.currentTimeMillis()) {
 val sufficient:Boolean get()=seconds>=2 && bytes>=1_000_000
}
interface DownloadTester {
 suspend fun download(url:String,expectedNetworkId:String,allowMetered:Boolean,progress:(Long,Double)->Unit):DownloadResult
}
object NetworkOverview {
 fun signal(rssi:Int?,transport:String="Wi-Fi"):String=when {
  transport!="Wi-Fi" -> "$transport：Wi-Fi 信号强弱不适用"
  rssi==null -> "信号：暂时无法判断，请连接 Wi-Fi 并检查权限"
  rssi>=-60 -> "信号强：手机与路由器之间的无线信号较好"
  rssi>=-67 -> "信号良好：日常使用的信号条件较好"
  rssi>=-75 -> "信号一般：隔墙或距离可能影响体验"
  else -> "信号弱：建议靠近路由器再比较"
 }
 fun speed(result:DownloadResult?):String=when {
  result==null -> "上网速度：尚未测速，不能仅凭信号强弱判断"
  !result.sufficient -> "采样时间或流量不足，暂不判断快慢，可换服务器重测"
  result.mbps>=50 -> "这次下载较快，通常能满足高清视频和大文件下载"
  result.mbps>=10 -> "这次下载速度适中，通常能满足网页和一般视频"
  else -> "这次下载较慢，视频或大文件下载可能需要等待"
 }
 fun mbps(bytes:Long,seconds:Double):Double { require(bytes>=0 && seconds>0);return bytes*8.0/seconds/1_000_000 }
}
