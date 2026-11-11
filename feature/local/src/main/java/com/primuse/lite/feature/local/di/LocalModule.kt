package com.primuse.lite.feature.local.di

import android.content.Context
import com.primuse.lite.core.database.dao.TrackDao
import com.primuse.lite.feature.local.saf.PrefsSafDirectoryStore
import com.primuse.lite.feature.local.saf.SafDirectoryStore
import com.primuse.lite.feature.local.saf.SafTrackSource
import com.primuse.lite.feature.local.scan.CompositeTrackSource
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
    fun provideSafDirectoryStore(@ApplicationContext context: Context): SafDirectoryStore =
        PrefsSafDirectoryStore(context)

    /**
     * 扫描流水线：SAF 自定义目录在前，MediaStore 在后。
     *
     * 顺序不能反：后写入者覆盖前者，MediaStore 的元数据（album / duration）更完整，
     * 必须最后写，否则会被 SAF 那份只有路径的记录覆盖掉。
     */
    @Provides
    @Singleton
    fun provideTrackPageSource(
        @ApplicationContext context: Context,
        safDirectoryStore: SafDirectoryStore,
    ): TrackPageSource = CompositeTrackSource(
        listOf(
            SafTrackSource(context, safDirectoryStore),
            MediaStoreTrackSource(context),
        ),
    )

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
