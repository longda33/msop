package com.primuse.lite.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.primuse.lite.core.database.dao.TrackDao
import com.primuse.lite.core.database.entity.TrackEntity

@Database(
    entities = [TrackEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PrimuseDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao

    companion object {
        const val NAME: String = "primuse-lite.db"
    }
}
