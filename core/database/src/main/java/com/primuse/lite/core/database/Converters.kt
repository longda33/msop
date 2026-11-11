package com.primuse.lite.core.database

import androidx.room.TypeConverter
import com.primuse.lite.core.model.SourceType

/** 把 [SourceType] 存成字符串，避免枚举序号变动导致数据错乱。 */
class Converters {

    @TypeConverter
    fun fromSourceType(value: SourceType): String = value.name

    @TypeConverter
    fun toSourceType(value: String): SourceType =
        runCatching { SourceType.valueOf(value) }.getOrDefault(SourceType.LOCAL)
}
