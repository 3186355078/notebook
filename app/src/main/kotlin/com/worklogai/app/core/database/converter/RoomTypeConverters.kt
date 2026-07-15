package com.worklogai.app.core.database.converter

import androidx.room.TypeConverter
import com.worklogai.app.core.model.ContentBlockType
import com.worklogai.app.core.model.SummaryStatus
import com.worklogai.app.core.model.SummaryType
import java.time.Instant
import java.time.LocalDate

class RoomTypeConverters {
    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun contentBlockTypeToString(value: ContentBlockType?): String? = value?.name

    @TypeConverter
    fun stringToContentBlockType(value: String?): ContentBlockType? = value?.let(ContentBlockType::valueOf)

    @TypeConverter
    fun summaryTypeToString(value: SummaryType?): String? = value?.name

    @TypeConverter
    fun stringToSummaryType(value: String?): SummaryType? = value?.let(SummaryType::valueOf)

    @TypeConverter
    fun summaryStatusToString(value: SummaryStatus?): String? = value?.name

    @TypeConverter
    fun stringToSummaryStatus(value: String?): SummaryStatus? = value?.let(SummaryStatus::valueOf)
}
