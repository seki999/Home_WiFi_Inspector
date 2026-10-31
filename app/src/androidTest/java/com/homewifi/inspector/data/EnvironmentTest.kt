package com.homewifi.inspector.data
import android.Manifest
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class EnvironmentTest {
 private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
 @Test fun snapshotUsesCurrentDefaultNetwork() {
  val cm=context.getSystemService(ConnectivityManager::class.java)
  val network=cm.activeNetwork;val caps=cm.getNetworkCapabilities(network)
  val snapshot=AndroidWifiSource(context).read()
  if(network!=null && caps!=null) {
   assertEquals(network.toString(),snapshot.networkId)
   assertEquals(ActiveNetworkProvider.transport(caps),snapshot.transport)
   if(snapshot.transport!="Wi-Fi") { assertNull(snapshot.rssi);assertNull(snapshot.ssid);assertNull(snapshot.tx) }
  } else { assertNull(snapshot.networkId);assertNull(snapshot.rssi) }
 }
 @Test fun deniedLocationDoesNotReturnFabricatedCoordinates()=runBlocking {
  assumeTrue(context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED && context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
  try { AndroidGeoLocator(context).current();fail("Expected permission denial") }
  catch(e:IllegalStateException) { assertTrue(e.message.orEmpty().contains("位置权限")) }
 }
 @Test fun gatewayGuardRejectsPublicTargetBeforeNetworkTraffic()=runBlocking {
  try { SocketNetworkTester(ActiveNetworkProvider(context)).gatewayPing("1.1.1.1");fail("Expected private gateway guard") }
  catch(e:IllegalArgumentException) { assertTrue(e.message.orEmpty().contains("私有")) }
 }
}
