package com.homewifi.inspector.data
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.homewifi.inspector.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
class AndroidGeoLocator @Inject constructor(@ApplicationContext private val context:Context):GeoLocator {
 @Suppress("DEPRECATION")
 override suspend fun current():GeoFix=withTimeout(15_000) {
  check(context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED || context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) { "需要位置权限，请在设置中授予（可选择大致位置）" }
  val lm=context.getSystemService(LocationManager::class.java)
  check(lm.isLocationEnabled) { "系统定位已关闭，请开启定位服务" }
  val precise=context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
  val providers=buildList {
   if(precise && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
   if(lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
   if(lm.getProviders(true).contains("fused")) add("fused")
  }.distinct()
  check(providers.isNotEmpty()) { "设备没有可用定位提供者；仍可手动命名位置" }
  suspendCancellableCoroutine { cont ->
   val signals=providers.map { CancellationSignal() }
   val completed=java.util.concurrent.atomic.AtomicBoolean(false)
   val remaining=java.util.concurrent.atomic.AtomicInteger(providers.size)
   cont.invokeOnCancellation { completed.set(true);signals.forEach { it.cancel() } }
   fun noFix() {
    if(remaining.decrementAndGet()==0 && completed.compareAndSet(false,true) && cont.isActive) cont.resumeWithException(IllegalStateException("未取得新鲜定位；室内可手动填写房间名称，或到窗边重试"))
   }
   providers.forEachIndexed { index,provider ->
    if(cont.isActive && !completed.get()) {
     try {
      LocationManagerCompat.getCurrentLocation(lm,provider,signals[index],Executor { it.run() }) { location ->
       if(!completed.get() && cont.isActive) {
        if(location==null || (SystemClock.elapsedRealtimeNanos()-location.elapsedRealtimeNanos) !in 0..60_000_000_000L) noFix()
        else if(completed.compareAndSet(false,true)) {
         val fix=GeoFix(location.latitude,location.longitude,if(location.hasAccuracy()) location.accuracy.toDouble() else null,location.time,if(Build.VERSION.SDK_INT>=31) location.isMock else location.isFromMockProvider)
         signals.forEach { it.cancel() }
         cont.resume(fix)
        }
       }
      }
     } catch(e:Exception) { noFix() }
    }
   }
  }
 }
}
