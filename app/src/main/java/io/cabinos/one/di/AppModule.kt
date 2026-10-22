package io.cabinos.one.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.cabinos.core.*
import io.cabinos.one.data.*
import io.cabinos.one.platform.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun database(@ApplicationContext context:Context)=Room.databaseBuilder(context,CabinDatabase::class.java,"cabinos.db").build()
    @Provides fun dao(db:CabinDatabase)=db.dao()
    @Provides @Singleton fun engine():UnifiedVehicleContextEngine {
        val hazards=DemoRoadHazardRepository().apply { seed(System.currentTimeMillis()) }
        return UnifiedVehicleContextEngine(hazards=hazards)
    }
    @Provides @Singleton fun runtimePreferences(@ApplicationContext context:Context)=RuntimePreferences(context)
    @Provides @Singleton fun simulator(preferences:RuntimePreferences)=SimulationVehicleProvider(preferences)
    @Provides @Singleton fun reminders(@ApplicationContext context:Context)=DepartureReminders(context)
    @Provides @Singleton fun speech(@ApplicationContext context:Context)=AndroidSpeechProvider(context)
    @Provides @Singleton fun location(@ApplicationContext context:Context)=AndroidLocationProvider(context)
    @Provides @Singleton fun nearby(@ApplicationContext context:Context)=NearbyPlaces(context)
    @Provides @Singleton fun navigation(@ApplicationContext context:Context,location:AndroidLocationProvider)=AndroidNavigationProvider(context,location)
    @Provides @Singleton fun sessions(dao:CabinDao):CabinSessionRepository=RoomCabinSessionRepository(dao)
    @Provides @Singleton fun audio(@ApplicationContext context:Context)=AmbientAudio(context)
}
