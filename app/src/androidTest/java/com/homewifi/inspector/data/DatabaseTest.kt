package com.homewifi.inspector.data
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class DatabaseTest {
 @Test fun saveReadAndDelete()=runBlocking {
  val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,InspectorDatabase::class.java).build()
  try {
   db.sessions().insert(Session(time=10,ssid="test",bssid=null,rssi=-67,frequency=5180,detail="real-test-record"))
   val rows=db.sessions().observe().first();assertEquals(1,rows.size);assertEquals(-67,rows.first().rssi)
   db.sessions().clear();assertTrue(db.sessions().observe().first().isEmpty())
  } finally { db.close() }
 }

 @Test fun locationNamesCanChangeWithoutChangingMeasurements()=runBlocking {
  val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,InspectorDatabase::class.java).build()
  try {
   db.locations().insert(LocationRecord(name="位置1",note="关门",time=100,ssid=null,bssid=null,rssi=-70,frequency=5180,mbps=25.0,bytes=10_000_000,seconds=3.2,server="https://test.invalid"))
   val original=db.locations().observe().first().single()
   db.locations().rename(original.id,"二楼卧室","开门")
   val updated=db.locations().observe().first().single()
   assertEquals("二楼卧室",updated.name);assertEquals("开门",updated.note)
   assertEquals(original.rssi,updated.rssi);assertEquals(original.mbps,updated.mbps);assertEquals(original.time,updated.time)
   db.locations().clear();assertTrue(db.locations().observe().first().isEmpty())
  } finally { db.close() }
 }
 @Test fun migrationKeepsExistingHistory()=runBlocking {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val name="homewifi-migration-test.db"
  context.deleteDatabase(name)
  val old=context.openOrCreateDatabase(name,0,null)
  old.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,time INTEGER NOT NULL,ssid TEXT,bssid TEXT,rssi INTEGER,frequency INTEGER,detail TEXT NOT NULL)")
  old.execSQL("INSERT INTO sessions(time,rssi,detail) VALUES (123,-65,'old-record')")
  old.version=1;old.close()
  val db=Room.databaseBuilder(context,InspectorDatabase::class.java,name).addMigrations(InspectorDatabase.MIGRATION_1_2,InspectorDatabase.MIGRATION_2_3).build()
  try {
   assertEquals("old-record",db.sessions().observe().first().single().detail)
   assertTrue(db.locations().observe().first().isEmpty())
  } finally { db.close();context.deleteDatabase(name) }
 }

 @Test fun v2LocationSurvivesV3MigrationWithoutInventedCoordinates()=runBlocking {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val name="homewifi-v2-migration-test.db";context.deleteDatabase(name)
  val old=context.openOrCreateDatabase(name,0,null)
  old.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,time INTEGER NOT NULL,ssid TEXT,bssid TEXT,rssi INTEGER,frequency INTEGER,detail TEXT NOT NULL)")
  old.execSQL("CREATE TABLE location_records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,name TEXT NOT NULL,note TEXT NOT NULL,time INTEGER NOT NULL,ssid TEXT,bssid TEXT,rssi INTEGER,frequency INTEGER,mbps REAL,bytes INTEGER,seconds REAL,server TEXT)")
  old.execSQL("INSERT INTO location_records(name,note,time,rssi,mbps) VALUES ('old-room','note',123,-60,25.0)")
  old.version=2;old.close()
  val db=Room.databaseBuilder(context,InspectorDatabase::class.java,name).addMigrations(InspectorDatabase.MIGRATION_2_3).build()
  try {
   val record=db.locations().observe().first().single()
   assertEquals("old-room",record.name);assertEquals("Wi-Fi",record.networkType)
   assertNull(record.latitude);assertNull(record.longitude);assertEquals(-60,record.rssi)
   assertEquals(25.0,record.mbps!!,.001)
   db.locations().insert(record.copy(id=0,name="new-room",networkType="移动网络",latitude=35.0,longitude=139.0,accuracy=30.0,geoTime=123,geoMock=true))
   val newRecord=db.locations().observe().first().first { it.name=="new-room" }
   assertEquals("移动网络",newRecord.networkType);assertEquals(true,newRecord.geoMock)
   assertEquals(35.0,newRecord.latitude!!,.001)
  } finally { db.close();context.deleteDatabase(name) }
 }
}
