package com.github.phisgr.eventviewer.services

import com.datastax.driver.core.*
import com.github.phisgr.eventviewer.util.*
import com.google.protobuf.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.guava.asDeferred
import kotlinx.coroutines.guava.await
import java.nio.ByteBuffer

interface CassandraService {
    fun close()

    suspend fun delete(t: ProtoWithSeqNr<TableConfig>)

    suspend fun update(t: ProtoWithSeqNr<TableConfig>)

    fun select(
        scope: CoroutineScope,
        persistenceId: String,
        from: Long,
        to: Long,
        config: TableConfig
    ): ReceiveChannel<ProtoWithSeqNr<TableConfig>>
}

class CassandraServiceImpl(contactPoints: List<String>) : CassandraService {

    private val cluster = Cluster.builder()
        .addContactPoints(*contactPoints.toTypedArray())
        .build()
    private val session = cluster.connect().also { it.init() }

    override fun close() {
        session.close()
        cluster.close()
    }

    class Statements(tableConfig: TableConfig, session: Session) {
        // Converting them to deferred because ListenableFuture.await() may cancel the future
        val psSelect: Deferred<PreparedStatement>
        val psUpdate: Deferred<PreparedStatement>
        val psDelete: Deferred<PreparedStatement>

        init {
            when (tableConfig) {
                is EventTableConfig -> {
                    psSelect = session.prepareAsync(selectEventStatement(tableConfig.tableName)).asDeferred()
                    psUpdate = session.prepareAsync(updateEventStatement(tableConfig.tableName)).asDeferred()
                    psDelete = session.prepareAsync(deleteEventStatement(tableConfig.tableName)).asDeferred()
                }
                is SnapshotTableConfig -> {
                    psSelect = session.prepareAsync(selectSnapshotStatement(tableConfig.tableName)).asDeferred()
                    psUpdate = session.prepareAsync(updateSnapshotStatement(tableConfig.tableName)).asDeferred()
                    psDelete = session.prepareAsync(deleteSnapshotStatement(tableConfig.tableName)).asDeferred()
                }
            }
        }

    }

    // set by main thread
    private val statementCache = mutableMapOf<TableConfig, Statements>()

    private fun statementsOf(config: TableConfig) = statementCache.computeIfAbsent(config) { Statements(it, session) }


    override suspend fun delete(t: ProtoWithSeqNr<TableConfig>) {
        val ps = statementsOf(t.config).psDelete.await()
        val bound = when (t) {
            is WrappedEvent -> {
                ps.bind(
                    t.persistenceId,
                    t.partitionNr,
                    t.seqNr,
                    t.timeUuid,
                    t.timebucket
                )
            }
            is WrappedSnapshot -> {
                ps.bind(
                    t.persistenceId,
                    t.seqNr
                )
            }
        }
        session.executeAsync(bound).await()
    }

    override suspend fun update(t: ProtoWithSeqNr<TableConfig>) {
        val serialized = ByteBuffer.wrap(t.data.toByteArray())

        val ps = statementsOf(t.config).psUpdate.await()
        val bound = when (t) {
            is WrappedEvent -> {
                ps.bind(
                    serialized,
                    t.persistenceId,
                    t.partitionNr,
                    t.seqNr,
                    t.timeUuid,
                    t.timebucket
                )
            }
            is WrappedSnapshot -> {
                ps.bind(
                    serialized,
                    t.persistenceId,
                    t.seqNr
                )
            }
        }
        session.executeAsync(bound).await()
    }

    override fun select(
        scope: CoroutineScope,
        persistenceId: String,
        from: Long, to: Long,
        config: TableConfig
    ) = when (config) {
        is EventTableConfig -> scope.selectEvents(persistenceId, from = from, to = to, config = config)
        is SnapshotTableConfig -> scope.selectSnapshots(persistenceId, from = from, to = to, config = config)
    }


    private fun CoroutineScope.selectSnapshots(
        persistenceId: String,
        from: Long,
        to: Long,
        config: SnapshotTableConfig
    ) = produce<WrappedSnapshot>(capacity = 64) {
        val ps = statementsOf(config).psSelect.await()
        val bound = ps.bind(persistenceId, from, to)
        val results = session.executeAsync(bound).await()
        loopResultSet(
            results, config, persistenceId, ::toSnapshot
        )
    }

    private fun CoroutineScope.selectEvents(
        persistenceId: String,
        from: Long,
        to: Long,
        config: EventTableConfig
    ): ReceiveChannel<WrappedEvent> {
        fun partitionNr(seqNr: Long): Long = (seqNr - 1) / config.partitionSize

        return produce(capacity = 64) {
            val ps = statementsOf(config).psSelect.await()

            // the first messages in the given range
            // may be written in an atomic write
            // that overshoots the TARGET_PARTITION_SIZE
            val startPartition = (partitionNr(from) - 1).coerceAtLeast(0L)

            for (partition in startPartition..partitionNr(to)) {
                val bound = ps.bind(persistenceId, partition, from, to)
                val results = session.executeAsync(bound).await()

                loopResultSet(results, config, persistenceId, ::toEvent)
            }
        }
    }

    private suspend fun <C : TableConfig, W : ProtoWithSeqNr<C>> ProducerScope<W>.loopResultSet(
        results: ResultSet,
        config: C,
        persistenceId: String,
        wrap: (Row, persistenceId: String, C) -> W
    ) {
        while (true) {
            val thisLoop = results.availableWithoutFetching
            if (thisLoop == 0) {
                if (results.isFullyFetched) {
                    break
                } else {
                    results.fetchMoreResults().await()
                }
            } else {
                repeat(thisLoop) {
                    // results.one() is non blocking when we have checked the rows are availableWithoutFetching
                    send(wrap(results.one(), persistenceId, config))
                }
            }
        }
    }

    private fun TableConfig.parseFromByteBuff(b: ByteBuffer): Message = emptyMessage.parserForType.parseFrom(b)

    private fun toEvent(row: Row, persistenceId: String, config: EventTableConfig): WrappedEvent {
        val b: ByteBuffer = row.getBytes("event")
        val parsed = config.parseFromByteBuff(b)

        return WrappedEvent(
            persistenceId = persistenceId,
            event = parsed,
            seqNr = row.getLong("sequence_nr"),
            partitionNr = row.getLong("partition_nr"),
            timeUuid = row.getUUID("timestamp"),
            timebucket = row.getString("timebucket"),
            config = config
        )
    }


    private fun toSnapshot(row: Row, persistenceId: String, config: SnapshotTableConfig): WrappedSnapshot {
        val b: ByteBuffer = row.getBytes("snapshot")
        val parsed = config.parseFromByteBuff(b)

        return WrappedSnapshot(
            snapshot = parsed,
            seqNr = row.getLong("sequence_nr"),
            persistenceId = persistenceId,
            config = config
        )
    }
}
