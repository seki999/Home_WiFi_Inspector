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
@Entity(tableName="location_records")
data class LocationRecord(@PrimaryKey(autoGenerate=true) val id:Long=0,val name:String,val note:String,val time:Long,val ssid:String?,val bssid:String?,val rssi:Int?,val frequency:Int?,val mbps:Double?,val bytes:Long?,val seconds:Double?,val server:String?,@ColumnInfo(defaultValue="'Wi-Fi'") val networkType:String="Wi-Fi",val latitude:Double?=null,val longitude:Double?=null,val accuracy:Double?=null,val geoTime:Long?=null,val geoMock:Boolean?=null)
@Dao interface LocationDao {
 @Query("SELECT * FROM location_records ORDER BY time DESC LIMIT 200") fun observe():Flow<List<LocationRecord>>
 @Insert suspend fun insert(record:LocationRecord)
 @Query("UPDATE location_records SET name=:name,note=:note WHERE id=:id") suspend fun rename(id:Long,name:String,note:String)
 @Query("DELETE FROM location_records") suspend fun clear()
}
@Database(entities=[Session::class,LocationRecord::class],version=3,exportSchema=false)
abstract class InspectorDatabase:RoomDatabase() { abstract fun sessions():SessionDao
 abstract fun locations():LocationDao
 companion object {
  val MIGRATION_2_3=object:androidx.room.migration.Migration(2,3) {
   override fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE location_records ADD COLUMN networkType TEXT NOT NULL DEFAULT 'Wi-Fi'")
    db.execSQL("ALTER TABLE location_records ADD COLUMN latitude REAL")
    db.execSQL("ALTER TABLE location_records ADD COLUMN longitude REAL")
    db.execSQL("ALTER TABLE location_records ADD COLUMN accuracy REAL")
    db.execSQL("ALTER TABLE location_records ADD COLUMN geoTime INTEGER")
    db.execSQL("ALTER TABLE location_records ADD COLUMN geoMock INTEGER")
   }
  }
  val MIGRATION_1_2=object:androidx.room.migration.Migration(1,2) {
   override fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS `location_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `note` TEXT NOT NULL, `time` INTEGER NOT NULL, `ssid` TEXT, `bssid` TEXT, `rssi` INTEGER, `frequency` INTEGER, `mbps` REAL, `bytes` INTEGER, `seconds` REAL, `server` TEXT)")
   }
  }
 }
}
private val Context.settings by preferencesDataStore("settings")
class Settings @Inject constructor(@ApplicationContext private val context:Context) {
 private val interval=longPreferencesKey("interval")
 val intervalMs=context.settings.data.map { it[interval] ?: 2000L }
 suspend fun setInterval(ms:Long) { require(ms in listOf(1000L,2000L,5000L));context.settings.edit { it[interval]=ms } }
}
