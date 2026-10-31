package com.homewifi.inspector.domain

object NetworkPolicy {
 const val DEFAULT_HOST="www.cloudflare.com"
 fun transport(vpn:Boolean,wifi:Boolean,cellular:Boolean,ethernet:Boolean):String=when {
  vpn -> "VPN";wifi -> "Wi-Fi";cellular -> "移动网络";ethernet -> "以太网";else -> "其他网络"
 }
 fun privateGateway(ip:String):Boolean {
  val p=ip.split('.').map { it.toIntOrNull() ?: return false }
  if(p.size!=4 || p.any { it !in 0..255 }) return false
  return p[0]==10 || (p[0]==172 && p[1] in 16..31) || (p[0]==192 && p[1]==168) || (p[0]==169 && p[1]==254)
 }
 fun httpsMeaning(code:Int):String=when(code) {
  in 200..299 -> "HTTPS 服务已响应；本次目标可访问"
  in 300..399 -> "收到重定向；可能是服务正常跳转或认证门户，请用浏览器确认"
  401,403 -> "服务器已响应但拒绝此请求；不代表断网，不绕过限制"
  407,511 -> "服务要求认证；请通过浏览器或网络管理员完成授权登录"
  in 400..499 -> "已收到服务器错误响应；检查目标地址或服务规则"
  in 500..599 -> "服务器或上游返回错误；不能单凭此判断本地 Wi-Fi 故障"
  else -> "收到 HTTP 状态码，尚不能判断网络是否正常"
 }
 fun freshGeo(fix:GeoFix?,now:Long):GeoFix?=fix?.takeIf { now-it.time in 0..120_000 }
}
data class GeoFix(val latitude:Double,val longitude:Double,val accuracy:Double?,val time:Long,val mock:Boolean)
interface GeoLocator { suspend fun current():GeoFix }
data class HttpsProbe(val code:Int,val totalMillis:Double)
