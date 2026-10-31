package com.homewifi.inspector.domain
import org.junit.Assert.*
import org.junit.Test
class NetworkPolicyTest {
 @Test fun transportRespectsVpnBeforeUnderlyingWifi() {
  assertEquals("VPN",NetworkPolicy.transport(true,true,true,false))
  assertEquals("Wi-Fi",NetworkPolicy.transport(false,true,false,false))
  assertEquals("移动网络",NetworkPolicy.transport(false,false,true,false))
  assertEquals("以太网",NetworkPolicy.transport(false,false,false,true))
 }
 @Test fun gatewayOnlyAcceptsPrivateIpv4() {
  listOf("192.168.1.1","10.0.0.1","172.16.0.1","172.31.255.254","169.254.1.1").forEach { assertTrue(it,NetworkPolicy.privateGateway(it)) }
  listOf("1.1.1.1","127.0.0.1","224.0.0.1","172.32.0.1","192.168.999.1","192.168.1","a.b.c.d","::1","192.168.1.1;echo").forEach { assertFalse(it,NetworkPolicy.privateGateway(it)) }
 }
 @Test fun deniedHttpIsNotLabeledDisconnected() {
  assertTrue(NetworkPolicy.httpsMeaning(403).contains("不代表断网"))
  assertTrue(NetworkPolicy.httpsMeaning(511).contains("认证"))
  assertTrue(NetworkPolicy.httpsMeaning(200).contains("本次目标可访问"))
  assertTrue(NetworkPolicy.httpsMeaning(302).contains("可能"))
 }
 @Test fun staleOrFutureCoordinatesAreNotAttached() {
  val fix=GeoFix(35.0,139.0,20.0,1_000,true)
  assertEquals(fix,NetworkPolicy.freshGeo(fix,121_000))
  assertNull(NetworkPolicy.freshGeo(fix,121_001));assertNull(NetworkPolicy.freshGeo(fix,999))
  assertNull(NetworkPolicy.freshGeo(null,1000))
 }
 @Test fun cellularAndVpnNeverPresentWifiSignalEvenIfRssiWasPassed() {
  assertTrue(NetworkOverview.signal(-40,"移动网络").contains("不适用"))
  assertTrue(NetworkOverview.signal(-40,"VPN").contains("不适用"))
 }
}
