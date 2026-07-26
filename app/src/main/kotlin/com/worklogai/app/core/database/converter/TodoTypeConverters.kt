package com.worklogai.app.core.database.converter

import androidx.room.TypeConverter
import com.worklogai.app.core.model.TodoPriority
import com.worklogai.app.core.model.TodoStatus

class TodoTypeConverters {
    @TypeConverter
    fun todoPriorityToString(value: TodoPriority?): String? = value?.name

    @TypeConverter
    fun stringToTodoPriority(value: String?): TodoPriority? = value?.let(TodoPriority::valueOf)

    @TypeConverter
    fun todoStatusToString(value: TodoStatus?): String? = value?.name

    @TypeConverter
    fun stringToTodoStatus(value: String?): TodoStatus? = value?.let(TodoStatus::valueOf)
}
