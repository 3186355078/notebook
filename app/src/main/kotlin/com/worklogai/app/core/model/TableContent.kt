package com.worklogai.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class TableContent(
    val title: String? = null,
    val columns: List<TableColumn> = emptyList(),
    val rows: List<TableRow> = emptyList(),
)

@Serializable
data class TableColumn(
    val id: String,
    val name: String,
)

@Serializable
data class TableRow(
    val id: String,
    val cells: Map<String, String> = emptyMap(),
)
