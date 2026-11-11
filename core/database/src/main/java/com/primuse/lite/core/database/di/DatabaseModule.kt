package com.primuse.lite.core.database.di

import android.content.Context
import androidx.room.Room
import com.primuse.lite.core.database.PrimuseDatabase
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
            // 自用工具，尚未发布任何版本；schema 变更时直接重建，不写迁移。
            // 一旦有真实用户数据，这里必须改成 addMigrations(...)。
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideTrackDao(database: PrimuseDatabase): TrackDao = database.trackDao()
}
