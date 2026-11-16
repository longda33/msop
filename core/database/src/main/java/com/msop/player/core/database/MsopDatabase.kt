package com.msop.player.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.msop.player.core.database.dao.FavoriteDao
import com.msop.player.core.database.dao.HiddenTrackDao
import com.msop.player.core.database.dao.PlaylistDao
import com.msop.player.core.database.dao.TrackDao
import com.msop.player.core.database.entity.FavoriteEntity
import com.msop.player.core.database.entity.HiddenTrackEntity
import com.msop.player.core.database.entity.PlaylistEntity
import com.msop.player.core.database.entity.PlaylistTrackEntity
import com.msop.player.core.database.entity.TrackEntity

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        FavoriteEntity::class,
        HiddenTrackEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MsopDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun favoriteDao(): FavoriteDao

    abstract fun hiddenTrackDao(): HiddenTrackDao

    companion object {
        const val NAME: String = "msop.db"

        /** v2 新增歌单两张表。曲目数据原样保留，升级后不用重扫。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlists` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist_tracks` (" +
                        "`playlistId` INTEGER NOT NULL, " +
                        "`trackId` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`playlistId`, `trackId`), " +
                        "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_playlist_tracks_playlistId` " +
                        "ON `playlist_tracks` (`playlistId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_playlist_tracks_trackId` " +
                        "ON `playlist_tracks` (`trackId`)",
                )
            }
        }

        /**
         * v3 增加音质字段（P7-06）。全部可空：旧曲目不需要重扫，播放/列表也不依赖它们。
         *
         * 用 ALTER TABLE 而不是重建表，避免把 5 万首曲目整表搬一遍。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tracks` ADD COLUMN `bitrateKbps` INTEGER")
                db.execSQL("ALTER TABLE `tracks` ADD COLUMN `sampleRateHz` INTEGER")
                db.execSQL("ALTER TABLE `tracks` ADD COLUMN `bitDepth` INTEGER")
            }
        }

        /**
         * v4（2026-11-16 用户需求「歌单支持拖动排序 + 收藏条目置顶」）：
         * - `playlists` 加 `sortOrder`：拖动排序后的位置，0 在最上面；
         * - 新建 `favorites` 表：收藏是虚拟歌单，单独存，跟曲目一起 CASCADE 清理。
         *
         * 老歌单按创建时间倒序回填一次，升级后列表顺序和升级前完全一致。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `playlists` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE `playlists` SET `sortOrder` = (" +
                        "SELECT COUNT(*) FROM `playlists` p2 " +
                        " WHERE p2.`createdAt` > `playlists`.`createdAt`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorites` (" +
                        "`trackId` TEXT NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`trackId`), " +
                        "FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_favorites_trackId` ON `favorites` (`trackId`)",
                )
            }
        }

        /**
         * v5（2026-11-16 晚用户需求「移除曲库 ⊆ 重扫不恢复 + 已移除歌曲可恢复」）：
         * 只加一张墓碑表，`tracks` 不动。
         *
         * 为什么不在 `tracks` 上加 `removed` 列：曲目入库是 `@Upsert` 整行覆盖，
         * 重扫时构造的实体 `removed` 默认 false，会把标记冲掉；独立表不参与 upsert。
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `hidden_tracks` (" +
                        "`id` TEXT NOT NULL, " +
                        "`removedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
            }
        }
    }
}
