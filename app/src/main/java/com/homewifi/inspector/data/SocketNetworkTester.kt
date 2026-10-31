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

class SocketNetworkTester @Inject constructor(private val networks:ActiveNetworkProvider):NetworkTester {
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
  val network=networks.current();val start=System.nanoTime();val addresses=resolve(network,host)
  return addresses.mapNotNull { it.hostAddress } to (System.nanoTime()-start)/1e6
 }
 override suspend fun tcp(host:String,port:Int,attempts:Int):List<Probe> {
  require(port in 1..65535);require(attempts in 1..30)
  val network=networks.current(); val addresses=resolve(network,host).take(4);check(addresses.isNotEmpty()) { "DNS 未返回地址" }
  return buildList {
   repeat(attempts) {
    currentCoroutineContext().ensureActive()
    networks.requireCurrent(network)
    var probe=Probe(null,"无法建立连接")
    // Try available IPv4/IPv6 candidates rather than treating the first address as the whole host.
    for(address in addresses) {
     currentCoroutineContext().ensureActive()
     networks.requireCurrent(network)
     probe=connect(network,address,port)
     if(probe.millis!=null) break
    }
    add(probe)
    delay(300)
   }
  }
 }
 private suspend fun connect(network:Network,address:InetAddress,port:Int):Probe=suspendCancellableCoroutine { cont ->
  val socket=network.socketFactory.createSocket()
  cont.invokeOnCancellation { runCatching { socket.close() } }
  Dispatchers.IO.dispatch(cont.context,Runnable {
   val start=System.nanoTime()
   val result=try {
    networks.requireCurrent(network)
    if(!cont.isActive) return@Runnable
    socket.connect(InetSocketAddress(address,port),1200)
    Probe((System.nanoTime()-start)/1e6,"TCP 建连成功（${address.hostAddress}）")
   } catch(e:SocketTimeoutException) { Probe(null,"连接超时（${address.hostAddress}）；检查端口、访问规则或目标服务") }
   catch(e:ConnectException) { Probe(null,"连接被拒绝（${address.hostAddress}）；目标服务可能未开放，不代表离线") }
   catch(e:SecurityException) { Probe(null,"权限不足或局域网访问受限") }
   catch(e:Exception) { Probe(null,"连接失败：${e.message}") }
   finally { runCatching { socket.close() } }
   if(cont.isActive) cont.resume(result)
  })
 }
 override suspend fun https(host:String):HttpsProbe {
  val network=networks.current()
  val url=okhttp3.HttpUrl.Builder().scheme("https").host(host).build()
  val client=okhttp3.OkHttpClient.Builder().socketFactory(network.socketFactory)
   .dns(object:okhttp3.Dns { override fun lookup(hostname:String)=network.getAllByName(hostname).toList() })
   .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
   .connectTimeout(4,java.util.concurrent.TimeUnit.SECONDS).callTimeout(8,java.util.concurrent.TimeUnit.SECONDS).build()
  val call=client.newCall(okhttp3.Request.Builder().url(url).head().header("Cache-Control","no-store").build())
  val start=System.nanoTime()
  return withTimeout(9000) { suspendCancellableCoroutine { cont ->
   cont.invokeOnCancellation { call.cancel() }
   call.enqueue(object:okhttp3.Callback {
    override fun onFailure(call:okhttp3.Call,e:java.io.IOException) { client.connectionPool.evictAll();if(cont.isActive) cont.resumeWithException(e) }
    override fun onResponse(call:okhttp3.Call,response:okhttp3.Response) {
     try { response.use { networks.requireCurrent(network);if(cont.isActive) cont.resume(HttpsProbe(it.code,(System.nanoTime()-start)/1e6)) } }
     catch(e:Exception) { if(cont.isActive) cont.resumeWithException(e) }
     finally { client.connectionPool.evictAll() }
    }
   })
  } }
 }
 override suspend fun gatewayPing(host:String):String {
  require(NetworkPolicy.privateGateway(host)) { "仅允许已授权的本地私有 IPv4 默认网关" }
  val network=networks.current();networks.requireCurrent(network)
  return suspendCancellableCoroutine { cont ->
   val process=java.util.concurrent.atomic.AtomicReference<Process?>()
   cont.invokeOnCancellation { process.get()?.destroyForcibly() }
   Dispatchers.IO.dispatch(cont.context,Runnable {
    try {
     if(!cont.isActive) return@Runnable
     val p=ProcessBuilder("/system/bin/ping","-n","-c","3","-W","1",host).redirectErrorStream(true).start()
     process.set(p)
     if(!cont.isActive) { p.destroyForcibly();return@Runnable }
     val finished=p.waitFor(5,java.util.concurrent.TimeUnit.SECONDS)
     if(!finished) p.destroyForcibly()
     val output=if(finished) p.inputStream.bufferedReader().use { it.readText().take(6000) } else "系统 ping 达到5秒上限，已停止"
     networks.requireCurrent(network)
     if(cont.isActive) cont.resume("ICMP：系统 ping 原始结果（不可用或未响应不能判定断网）\n$output")
    } catch(e:Exception) { if(cont.isActive) cont.resume("ICMP 当前设备无法完成：${e.message}") }
    finally { process.get()?.destroyForcibly() }
   })
  }
 }

}
