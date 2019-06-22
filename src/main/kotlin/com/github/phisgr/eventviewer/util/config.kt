package com.github.phisgr.eventviewer.util

import com.google.protobuf.Message

sealed class TableConfig(className: String) {
    abstract val tableName: String
    abstract val className: String

    val emptyMessage = ClassLoader.getSystemClassLoader().loadClass(className)
        .getDeclaredMethod("getDefaultInstance")
        .invoke(null) as Message
}

data class EventTableConfig(
    override val tableName: String,
    override val className: String,
    val partitionSize: Long
) : TableConfig(className)

data class SnapshotTableConfig(
    override val tableName: String,
    override val className: String
) : TableConfig(className)

data class TableConfigs(val messages: List<EventTableConfig>, val snapshots: List<SnapshotTableConfig>)
