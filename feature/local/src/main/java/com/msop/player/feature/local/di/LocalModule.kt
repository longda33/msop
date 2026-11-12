package com.msop.player.feature.local.di

import android.content.Context
import com.msop.player.core.common.saf.SafRootProvider
import com.msop.player.core.database.dao.TrackDao
import com.msop.player.feature.local.metadata.AndroidMetadataReader
import com.msop.player.feature.local.metadata.CoverCache
import com.msop.player.feature.local.metadata.CoverProvider
import com.msop.player.feature.local.metadata.LocalMetadataEnricher
import com.msop.player.feature.local.metadata.MetadataReader
import com.msop.player.feature.local.metadata.MetadataStore
import com.msop.player.feature.local.metadata.RoomMetadataStore
import com.msop.player.feature.local.saf.PrefsSafDirectoryStore
import com.msop.player.feature.local.saf.SafDirectoryStore
import com.msop.player.feature.local.saf.SafTrackSource
import com.msop.player.feature.local.scan.CompositeTrackSource
import com.msop.player.feature.local.scan.LocalLibrarySyncer
import com.msop.player.feature.local.scan.LocalTrackStore
import com.msop.player.feature.local.scan.MediaStoreTrackSource
import com.msop.player.feature.local.scan.RoomLocalTrackStore
import com.msop.player.feature.local.scan.TrackPageSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocalModule {

    @Provides
    @Singleton
    fun provideSafDirectoryStore(@ApplicationContext context: Context): SafDirectoryStore =
        PrefsSafDirectoryStore(context)

    /** 把 SAF 目录树暴露给别的 feature（歌词要顺着树找同名 .lrc）。 */
    @Provides
    @Singleton
    fun provideSafRootProvider(store: SafDirectoryStore): SafRootProvider =
        SafRootProvider { store.treeUris() }

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

    @Provides
    @Singleton
    fun provideMetadataReader(@ApplicationContext context: Context): MetadataReader =
        AndroidMetadataReader(context)

    @Provides
    @Singleton
    fun provideCoverCache(@ApplicationContext context: Context): CoverCache =
        CoverCache(File(context.filesDir, CoverCache.DIR_NAME))

    @Provides
    @Singleton
    fun provideMetadataStore(dao: TrackDao): MetadataStore = RoomMetadataStore(dao)

    @Provides
    @Singleton
    fun provideCoverProvider(
        store: MetadataStore,
        reader: MetadataReader,
        cache: CoverCache,
    ): CoverProvider = CoverProvider(store, reader, cache)

    @Provides
    @Singleton
    fun provideLocalMetadataEnricher(
        store: MetadataStore,
        reader: MetadataReader,
        covers: CoverProvider,
        cache: CoverCache,
    ): LocalMetadataEnricher = LocalMetadataEnricher(store, reader, covers, cache)
}
