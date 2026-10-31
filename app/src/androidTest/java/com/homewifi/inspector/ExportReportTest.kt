package com.homewifi.inspector
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homewifi.inspector.data.Session
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class ExportReportTest {
 private val row=Session(time=1234,ssid="PRIVATE_SSID",bssid="aa:bb:cc:dd:ee:ff",rssi=-60,frequency=5180,detail="IP 192.168.1.5 target secret.example")
 @Test fun redactionRemovesAllNetworkIdentifiers() {
  listOf("json","csv").forEach { format ->
   val result=exportReport(listOf(row),true,format)
   listOf("PRIVATE_SSID","aa:bb","192.168.1.5","secret.example").forEach { assertFalse(result.contains(it)) }
   assertTrue(result.contains("5180"));assertTrue(result.contains("-60"))
  }
 }
 @Test fun unredactedCsvEscapesQuotesAndFormula() {
  val result=exportReport(listOf(row.copy(ssid="=1+1",detail="a,\"b\"")),false,"csv")
  assertTrue(result.contains("'=1+1"));assertTrue(result.contains("a,\"\"b\"\""))
 }
}
