package com.homewifi.inspector.data
import android.content.Context
import androidx.room.*
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

@Entity(tableName="sessions")
data class Session(@PrimaryKey(autoGenerate=true) val id:Long=0,val time:Long,val ssid:String?,val bssid:String?,val rssi:Int?,val frequency:Int?,val detail:String)
@Dao interface SessionDao {
 @Query("SELECT * FROM sessions ORDER BY time DESC LIMIT 200") fun observe():Flow<List<Session>>
 @Insert suspend fun insert(session:Session)
 @Query("DELETE FROM sessions") suspend fun clear()
}
@Database(entities=[Session::class],version=1,exportSchema=false)
abstract class InspectorDatabase:RoomDatabase() { abstract fun sessions():SessionDao }
private val Context.settings by preferencesDataStore("settings")
class Settings @Inject constructor(@ApplicationContext private val context:Context) {
 private val interval=longPreferencesKey("interval")
 val intervalMs=context.settings.data.map { it[interval] ?: 2000L }
 suspend fun setInterval(ms:Long) { require(ms in listOf(1000L,2000L,5000L));context.settings.edit { it[interval]=ms } }
}
