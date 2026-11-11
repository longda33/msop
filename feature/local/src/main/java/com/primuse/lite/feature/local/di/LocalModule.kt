package com.primuse.lite.feature.local.di

import android.content.Context
import com.primuse.lite.core.database.dao.TrackDao
import com.primuse.lite.feature.local.scan.LocalLibrarySyncer
import com.primuse.lite.feature.local.scan.LocalTrackStore
import com.primuse.lite.feature.local.scan.MediaStoreTrackSource
import com.primuse.lite.feature.local.scan.RoomLocalTrackStore
import com.primuse.lite.feature.local.scan.TrackPageSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocalModule {

    @Provides
    @Singleton
    fun provideTrackPageSource(@ApplicationContext context: Context): TrackPageSource =
        MediaStoreTrackSource(context)

    @Provides
    @Singleton
    fun provideLocalTrackStore(dao: TrackDao): LocalTrackStore = RoomLocalTrackStore(dao)

    @Provides
    @Singleton
    fun provideLocalLibrarySyncer(
        source: TrackPageSource,
        store: LocalTrackStore,
    ): LocalLibrarySyncer = LocalLibrarySyncer(source, store)
}
