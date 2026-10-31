package com.homewifi.inspector.data
import android.content.Context
import android.net.*
import android.os.CancellationSignal
import com.homewifi.inspector.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.net.*
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SocketNetworkTester @Inject constructor(@ApplicationContext context:Context):NetworkTester {
 private val cm=context.getSystemService(ConnectivityManager::class.java)
 private fun wifi()=cm.allNetworks.firstOrNull { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true } ?: error("需要连接 Wi-Fi；不会回退到移动网络")
 private suspend fun resolve(network:Network,host:String):List<InetAddress> = withTimeout(5000) {
  suspendCancellableCoroutine { continuation ->
   if(InetAddresses.isNumericAddress(host)) { continuation.resume(listOf(InetAddresses.parseNumericAddress(host)));return@suspendCancellableCoroutine }
   val signal=CancellationSignal()
   continuation.invokeOnCancellation { signal.cancel() }
   DnsResolver.getInstance().query(network,host,DnsResolver.FLAG_NO_CACHE_LOOKUP,Executor { it.run() },signal,
    object:DnsResolver.Callback<List<InetAddress>> {
     override fun onAnswer(answer:List<InetAddress>,rcode:Int) { if(continuation.isActive) { if(rcode==0) continuation.resume(answer) else continuation.resumeWithException(IllegalStateException("DNS rcode=$rcode")) } }
     override fun onError(error:DnsResolver.DnsException) { if(continuation.isActive) continuation.resumeWithException(error) }
    })
  }
 }
 override suspend fun dns(host:String):Pair<List<String>,Double> {
  require(!InetAddresses.isNumericAddress(host)) { "DNS 测试请输入域名" }
  val network=wifi();val start=System.nanoTime();val addresses=resolve(network,host)
  return addresses.mapNotNull { it.hostAddress } to (System.nanoTime()-start)/1e6
 }
 override suspend fun tcp(host:String,port:Int,attempts:Int):List<Probe> {
  require(port in 1..65535);require(attempts in 1..30)
  val network=wifi(); val address=resolve(network,host).firstOrNull() ?: error("DNS 未返回地址")
  return buildList {
   repeat(attempts) {
    currentCoroutineContext().ensureActive()
    add(withContext(Dispatchers.IO) {
     val socket=network.socketFactory.createSocket()
     val start=System.nanoTime()
     try { socket.use { it.connect(InetSocketAddress(address,port),1500) }; Probe((System.nanoTime()-start)/1e6,"TCP 建连成功") }
     catch(e:SocketTimeoutException) { Probe(null,"连接超时") }
     catch(e:ConnectException) { Probe(null,"连接被拒绝/无法建立；不代表离线") }
     catch(e:SecurityException) { Probe(null,"权限不足或局域网访问受限") }
     catch(e:java.io.IOException) { Probe(null,"连接失败：${e.javaClass.simpleName}") }
     finally { socket.close() }
    })
    delay(300)
   }
  }
 }
}
