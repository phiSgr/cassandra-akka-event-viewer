package com.github.phisgr.eventviewer.util

import com.google.protobuf.Message
import io.reactivex.subjects.BehaviorSubject
import java.util.*

sealed class ProtoWithSeqNr<out C : TableConfig> {
    abstract val data: Message
    abstract val seqNr: Long
    abstract val persistenceId: String

    abstract fun withData(data: Message): ProtoWithSeqNr<C>
    abstract val config: C

    val modificationState = BehaviorSubject.createDefault(ModificationState.UNCHANGED)

    enum class ModificationState {
        UNCHANGED, MODIFIED, DELETED
    }
}

data class WrappedEvent(
    val event: Message,
    override val seqNr: Long,
    // used for modification
    override val persistenceId: String,
    val partitionNr: Long,
    val timeUuid: UUID,
    val timebucket: String,
    override val config: EventTableConfig
) : ProtoWithSeqNr<EventTableConfig>() {
    override val data: Message
        get() = event

    override fun withData(data: Message) = copy(event = data)
}

data class WrappedSnapshot(
    val snapshot: Message,
    override val seqNr: Long,
    override val persistenceId: String,
    override val config: SnapshotTableConfig
) : ProtoWithSeqNr<SnapshotTableConfig>() {
    override val data: Message
        get() = snapshot

    override fun withData(data: Message) = copy(snapshot = data)
}
