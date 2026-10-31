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
}
