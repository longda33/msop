package com.primuse.lite.core.database.di

import android.content.Context
import androidx.room.Room
import com.primuse.lite.core.database.PrimuseDatabase
import com.primuse.lite.core.database.dao.PlaylistDao
import com.primuse.lite.core.database.dao.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PrimuseDatabase =
        Room.databaseBuilder(context, PrimuseDatabase::class.java, PrimuseDatabase.NAME)
            .addMigrations(PrimuseDatabase.MIGRATION_1_2)
            // 兜底：万一未来漏写迁移，宁可重建也不能启动崩溃。
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideTrackDao(database: PrimuseDatabase): TrackDao = database.trackDao()

    @Provides
    fun providePlaylistDao(database: PrimuseDatabase): PlaylistDao = database.playlistDao()
}
