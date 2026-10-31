package com.homewifi.inspector.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.homewifi.inspector.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HttpsDownloadTester @Inject constructor(private val networks:ActiveNetworkProvider):DownloadTester {
 override suspend fun download(url:String,expectedNetworkId:String,allowMetered:Boolean,progress:(Long,Double)->Unit):DownloadResult {
  val parsed=url.toHttpUrlOrNull() ?: error("请输入有效 HTTPS 测速地址")
  require(parsed.isHttps && parsed.username.isEmpty() && parsed.password.isEmpty()) { "仅支持不含账号密码的 HTTPS 地址" }
  val network=networks.current()
  check(network.toString()==expectedNetworkId) { "网络已变化，请重新确认测速网络和流量" }
  networks.requireCurrent(network,allowMetered)
  val client=OkHttpClient.Builder().socketFactory(network.socketFactory).dns(object:Dns { override fun lookup(hostname:String):List<java.net.InetAddress> = network.getAllByName(hostname).toList() })
   .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
   .connectTimeout(5,TimeUnit.SECONDS).readTimeout(2,TimeUnit.SECONDS).callTimeout(18,TimeUnit.SECONDS).build()
  val request=Request.Builder().url(parsed).header("Cache-Control","no-cache, no-store").header("Accept-Encoding","identity").build()
  val active=java.util.concurrent.atomic.AtomicReference<Call?>()
  val start=System.nanoTime()
  return kotlinx.coroutines.withTimeout(18_000) {
   suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { active.get()?.cancel() }
    client.dispatcher.executorService.execute {
     try {
      var bytes=0L;var lastUpdate=0L;var rounds=0
      while(continuation.isActive && bytes<100_000_000L && (System.nanoTime()-start)<10_000_000_000L && rounds<4) {
       networks.requireCurrent(network,allowMetered)
       rounds++
       val call=client.newCall(request);active.set(call)
       if(!continuation.isActive) { call.cancel();break }
       call.execute().use { response ->
        check(response.isSuccessful) { "测速服务器返回 HTTP ${response.code}" }
        check(response.header("Content-Encoding").let { it==null || it.equals("identity",true) }) { "服务器返回压缩数据，无法可靠计算下载流量" }
        val body=response.body ?: error("服务器未提供下载数据")
        val type=body.contentType()?.toString().orEmpty()
        check(!type.contains("text/html",true) && !type.contains("json",true)) { "该地址返回网页或JSON，请使用专用下载测速端点" }
        body.byteStream().use { stream ->
         val buffer=ByteArray(64*1024)
         while(continuation.isActive && bytes<100_000_000L && (System.nanoTime()-start)<10_000_000_000L) {
          networks.requireCurrent(network,allowMetered)
          val count=stream.read(buffer,0,minOf(buffer.size.toLong(),100_000_000L-bytes).toInt())
          if(count<0) break
          bytes+=count
          val now=System.nanoTime()
          if(continuation.isActive && now-lastUpdate>500_000_000L) { progress(bytes,(now-start)/1e9);lastUpdate=now }
         }
        }
       }
      }
      val seconds=(System.nanoTime()-start)/1e9
      check(bytes>0) { "没有收到测速数据" }
      if(continuation.isActive) continuation.resume(DownloadResult(NetworkOverview.mbps(bytes,seconds),bytes,seconds,parsed.toString()))
     } catch(e:Exception) { if(continuation.isActive) continuation.resumeWithException(e) }
     finally { active.get()?.cancel();client.connectionPool.evictAll() }
    }
   }
  }
 }
}
