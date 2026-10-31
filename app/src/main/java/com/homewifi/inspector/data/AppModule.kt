package com.homewifi.inspector.data
import android.content.Context
import androidx.room.Room
import com.homewifi.inspector.domain.*
import dagger.*
import dagger.hilt.*
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton
@Module @InstallIn(SingletonComponent::class)
object AppModule {
 @Provides @Singleton fun locator(impl:AndroidGeoLocator):GeoLocator=impl
 @Provides @Singleton fun source(impl:AndroidWifiSource):WifiSource=impl
 @Provides @Singleton fun downloader(impl:HttpsDownloadTester):DownloadTester=impl
 @Provides @Singleton fun tester(impl:SocketNetworkTester):NetworkTester=impl
 @Provides @Singleton fun database(@ApplicationContext c:Context):InspectorDatabase=Room.databaseBuilder(c,InspectorDatabase::class.java,"inspector.db").addMigrations(InspectorDatabase.MIGRATION_1_2,InspectorDatabase.MIGRATION_2_3).build()
 @Provides fun dao(db:InspectorDatabase)=db.sessions()
 @Provides fun locationDao(db:InspectorDatabase)=db.locations()
}
