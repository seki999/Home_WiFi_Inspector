package com.homewifi.inspector.domain

data class WifiSnapshot(
 val time: Long = System.currentTimeMillis(), val networkId:String? = null, val status: String = "等待采样",
 val transport:String = "未连接", val metered:Boolean = false,
 val ssid: String? = null, val bssid: String? = null, val rssi: Int? = null,
 val frequency: Int? = null, val tx: Int? = null, val rx: Int? = null,
 val standard: String? = null, val security: String? = null,
 val addresses: List<String> = emptyList(), val gateway: String? = null,
 val dns: List<String> = emptyList(), val validated: Boolean = false,
 val internet: Boolean = false, val captive: Boolean = false,
 val privateDns: String? = null, val mlo: String? = null
)
data class AccessPoint(val ssid: String, val bssid: String, val rssi: Int, val frequency: Int,
 val width: Int, val center: Int, val capabilities: String, val seenMicros: Long)
data class ScanState(val message: String = "尚未扫描", val points: List<AccessPoint> = emptyList())
data class Probe(val millis: Double?, val outcome: String)
data class LatencyStats(val count: Int, val success: Int, val min: Double?, val max: Double?,
 val mean: Double?, val median: Double?, val p95: Double?, val jitter: Double?, val failureRate: Double)
interface WifiSource {
 fun read(): WifiSnapshot
 fun changes(): kotlinx.coroutines.flow.Flow<String>
 fun scans(): kotlinx.coroutines.flow.Flow<ScanState>
 fun requestScan(): String
}
interface NetworkTester { suspend fun tcp(host: String, port: Int, attempts: Int = 5): List<Probe>; suspend fun dns(host: String): Pair<List<String>, Double>; suspend fun https(host:String):HttpsProbe; suspend fun gatewayPing(host:String):String }
