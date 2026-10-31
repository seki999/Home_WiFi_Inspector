package com.homewifi.inspector.domain
import org.junit.Assert.*
import org.junit.Test
class AnalysisTest {
 @Test fun rssiBoundaries() {
  val expected=mapOf(-49 to "非常强",-50 to "非常强",-51 to "强",-60 to "强",-61 to "良好",-67 to "良好",-68 to "一般",-75 to "一般",-76 to "弱",-85 to "弱",-86 to "非常弱")
  expected.forEach { (r,g) -> assertEquals(g,Analysis.grade(r)) }
 }
 @Test fun channelFrequency() {
  mapOf(2412 to 1,2472 to 13,2484 to 14,5180 to 36,5935 to 2,5955 to 1,7115 to 233,4910 to 182).forEach { (f,c) -> assertEquals(c,Analysis.channel(f)) }
  assertNull(Analysis.channel(2413));assertNull(Analysis.channel(0))
  assertEquals("6 GHz",Analysis.band(5955));assertEquals("5 GHz",Analysis.band(5180))
 }
 @Test fun missingScoreStaysMissing() { assertNull(Analysis.score(null));assertEquals(100,Analysis.score(-40));assertEquals(0,Analysis.score(-100));assertEquals(50,Analysis.score(-70)) }
 @Test fun measuredFailureLowersScore() { assertTrue(Analysis.score(-50,failure=1.0)!! < Analysis.score(-50)!!) }
 @Test fun statisticsIncludeFailuresButNoInventedLatency() {
  val s=Analysis.stats(listOf(Probe(10.0,"ok"),Probe(20.0,"ok"),Probe(null,"timeout"),Probe(40.0,"ok")))
  assertEquals(4,s.count);assertEquals(3,s.success);assertEquals(.25,s.failureRate,.0001)
  assertEquals(10.0,s.min!!,.001);assertEquals(40.0,s.max!!,.001);assertEquals(20.0,s.median!!,.001)
  assertEquals(40.0,s.p95!!,.001);assertEquals(10.0,s.jitter!!,.001)
 }
 @Test fun allFailuresHaveNoStats() { val s=Analysis.stats(listOf(Probe(null,"refused")));assertNull(s.mean);assertNull(s.jitter);assertEquals(1.0,s.failureRate,.001) }
 @Test fun evenMedianAndEmpty() { assertEquals(15.0,Analysis.stats(listOf(Probe(10.0,""),Probe(20.0,""))).median!!,.001);assertNull(Analysis.stats(emptyList()).p95) }
 @Test fun congestionUsesOverlapAndBand() {
  val ap=AccessPoint("x","a",-50,2412,0,2412,"",0)
  assertTrue(Analysis.congestion(2412,20,listOf(ap))>0)
  assertEquals(0.0,Analysis.congestion(2462,20,listOf(ap)),.001)
  assertEquals(0.0,Analysis.congestion(5955,20,listOf(ap)),.001)
 }
 @Test fun adviceRequiresEvidence() {
  assertFalse(Analysis.advice(WifiSnapshot(validated=true)).any { it.contains("加密风险") })
  assertTrue(Analysis.advice(WifiSnapshot(security="WEP")).any { it.contains("加密风险") })
  assertTrue(Analysis.advice(WifiSnapshot(rssi=-80)).any { it.contains("单次采样") })
 }
}
