package com.msop.player.core.database.di

import android.content.Context
import androidx.room.Room
import com.msop.player.core.database.MsopDatabase
import com.msop.player.core.database.dao.PlaylistDao
import com.msop.player.core.database.dao.TrackDao
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
    fun provideDatabase(@ApplicationContext context: Context): MsopDatabase =
        Room.databaseBuilder(context, MsopDatabase::class.java, MsopDatabase.NAME)
            .addMigrations(MsopDatabase.MIGRATION_1_2)
            // 兜底：万一未来漏写迁移，宁可重建也不能启动崩溃。
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideTrackDao(database: MsopDatabase): TrackDao = database.trackDao()

    @Provides
    fun providePlaylistDao(database: MsopDatabase): PlaylistDao = database.playlistDao()
}
