package com.msop.player.core.database.di

import android.content.Context
import androidx.room.Room
import com.msop.player.core.database.MsopDatabase
import com.msop.player.core.database.dao.FavoriteDao
import com.msop.player.core.database.dao.HiddenTrackDao
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
            .addMigrations(
                MsopDatabase.MIGRATION_1_2,
                MsopDatabase.MIGRATION_2_3,
                MsopDatabase.MIGRATION_3_4,
                MsopDatabase.MIGRATION_4_5,
            )
            // 2026-11-17 修复：删掉 `fallbackToDestructiveMigration`。
            // 它会在「漏写迁移」时静默清库——用户 509 首本机曲、云端曲目、歌单、
            // 收藏、播放计数全部消失，而界面只表现为「曲库空了」。宁可启动时
            // 明确抛出 IllegalStateException（开发阶段立刻暴露），也不能静默删数据。
            // v1→v5 五条迁移齐备；新增版本号时必须同步补一条 Migration。
            .build()

    @Provides
    fun provideTrackDao(database: MsopDatabase): TrackDao = database.trackDao()

    @Provides
    fun providePlaylistDao(database: MsopDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun provideFavoriteDao(database: MsopDatabase): FavoriteDao = database.favoriteDao()

    /** 「已移除歌曲」墓碑表 DAO（v5 表）。 */
    @Provides
    fun provideHiddenTrackDao(database: MsopDatabase): HiddenTrackDao = database.hiddenTrackDao()
}
