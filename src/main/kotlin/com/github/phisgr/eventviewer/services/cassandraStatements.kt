package com.github.phisgr.eventviewer.services

fun selectEventStatement(tableName: String) = """
    |SELECT * FROM $tableName WHERE
    |  persistence_id = ? AND
    |  partition_nr = ? AND
    |  sequence_nr >= ? AND
    |  sequence_nr <= ?
    """.trimMargin()

fun deleteEventStatement(tableName: String) = """
    |DELETE FROM $tableName
    |  WHERE
    |    persistence_id = ? AND
    |    partition_nr = ? AND
    |    sequence_nr = ? AND
    |    timestamp = ? AND
    |    timebucket = ?
    """.trimMargin()

fun updateEventStatement(tableName: String) = """
    |UPDATE $tableName
    |  SET
    |    event = ?
    |  WHERE
    |    persistence_id = ? AND
    |    partition_nr = ? AND
    |    sequence_nr = ? AND
    |    timestamp = ? AND
    |    timebucket = ?
    """.trimMargin()


fun selectSnapshotStatement(tableName: String) = """
    |SELECT * FROM $tableName WHERE
    |  persistence_id = ? AND
    |  sequence_nr >= ? AND
    |  sequence_nr <= ?
    """.trimMargin()


fun deleteSnapshotStatement(tableName: String) = """
    |DELETE FROM $tableName
    |  WHERE
    |    persistence_id = ? AND
    |    sequence_nr = ?
    """.trimMargin()


fun updateSnapshotStatement(tableName: String) = """
    |UPDATE $tableName
    |  SET
    |    snapshot = ?
    |  WHERE
    |    persistence_id = ? AND
    |    sequence_nr = ?
    """.trimMargin()
