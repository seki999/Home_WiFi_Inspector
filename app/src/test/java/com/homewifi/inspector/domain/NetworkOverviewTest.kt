package com.homewifi.inspector.domain
import org.junit.Assert.*
import org.junit.Test
class NetworkOverviewTest {
 @Test fun signalUsesMeasuredRssiOnly() {
  assertTrue(NetworkOverview.signal(null).contains("无法判断"))
  assertTrue(NetworkOverview.signal(-60).startsWith("信号强"))
  assertTrue(NetworkOverview.signal(-67).startsWith("信号良好"))
  assertTrue(NetworkOverview.signal(-75).startsWith("信号一般"))
  assertTrue(NetworkOverview.signal(-76).startsWith("信号弱"))
 }
 @Test fun noDownloadDoesNotInferSpeedFromSignal() { assertTrue(NetworkOverview.speed(null).contains("尚未测速")) }
 @Test fun insufficientSamplesNeverGetFastRating() {
  assertTrue(NetworkOverview.speed(DownloadResult(900.0,100_000_000,1.0,"test")).contains("不足"))
  assertTrue(NetworkOverview.speed(DownloadResult(.1,1000,10.0,"test")).contains("不足"))
 }
 @Test fun measuredSpeedThresholds() {
  fun result(speed:Double)=DownloadResult(speed,5_000_000,3.0,"test")
  assertTrue(NetworkOverview.speed(result(50.0)).contains("较快"))
  assertTrue(NetworkOverview.speed(result(10.0)).contains("适中"))
  assertTrue(NetworkOverview.speed(result(9.9)).contains("较慢"))
 }
 @Test fun bytesToMegabitsIsNotMegabytes() { assertEquals(8.0,NetworkOverview.mbps(10_000_000,10.0),.0001) }
 @Test(expected=IllegalArgumentException::class) fun rejectsZeroDuration() { NetworkOverview.mbps(10,0.0) }
}
