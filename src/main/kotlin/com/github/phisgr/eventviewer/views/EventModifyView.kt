package com.github.phisgr.eventviewer.views

import com.github.difflib.DiffUtils
import com.github.difflib.patch.*
import com.github.phisgr.eventviewer.app.Styles
import com.github.phisgr.eventviewer.services.CassandraService
import com.github.phisgr.eventviewer.util.*
import com.github.thomasnield.rxkotlinfx.toObservable
import io.reactivex.rxjavafx.schedulers.JavaFxScheduler
import io.reactivex.subjects.BehaviorSubject
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.layout.VBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tornadofx.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

class EventModifyView(
    event: ProtoWithSeqNr<TableConfig>
) : Fragment(), CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onDelete() {
        job.cancel()
        super.onDelete()
    }

    private val cassandraService: CassandraService by di()

    private val original = event.data.prettyPrint()
    private val split = original.split('\n')

    private val modified = BehaviorSubject.createDefault<String>(original)

    override val root = splitpane {
        orientation = Orientation.HORIZONTAL

        this += borderpane {
            top = label("Original")
            center = textarea(original) {
                isEditable = false
                addClass(Styles.nonEditable)
            }
        }

        this += borderpane {
            top = label("Diff")
            center = listview<AbstractDelta<String>> {
                isEditable = false
                modified
                    .debounce(100, TimeUnit.MILLISECONDS).map { mod ->
                        DiffUtils.diff(split, mod.split('\n')).deltas
                    }
                    .observeOn(JavaFxScheduler.platform())
                    .subscribe {
                        this.items.setAll(it)
                    }.let { subscription ->
                        job.invokeOnCompletion { subscription.dispose() }
                    }

                cellFormat {
                    graphic = vbox {
                        when (item) {
                            is ChangeDelta -> {
                                showChunk(item.source).addClass(Styles.del)
                                showChunk(item.target).addClass(Styles.ins)
                            }
                            is DeleteDelta -> {
                                showChunk(item.source).addClass(Styles.del)
                            }
                            is InsertDelta -> {
                                showChunk(item.target).addClass(Styles.ins)
                            }
                        }
                    }
                }
            }

            bottom = button("SAVE") {
                addClass(Styles.scary)
                this.prefWidthProperty().bind(this@borderpane.widthProperty())
                action {
                    val newEvent = event.withData(fromJson(event.data, modified.value!!))
                    log.info { "Changing ${event.data.json()} to ${newEvent.data.json()}" }
                    launch {
                        this@button.isDisable = true
                        cassandraService.update(newEvent)

                        val modified = event.data != newEvent.data
                        event.modificationState.onNext(
                            if (modified)
                                ProtoWithSeqNr.ModificationState.MODIFIED else
                                ProtoWithSeqNr.ModificationState.UNCHANGED
                        )
                        this@EventModifyView.close()
                    }
                }
            }
        }
        this += borderpane {
            top = label("Modified")
            center = textarea(original) {
                textProperty().toObservable().subscribe(modified)
            }
        }

        setDividerPositions(1.0 / 3, 2.0 / 3)
    }

    private fun VBox.showChunk(chunk: Chunk<String>): Node {
        return this@showChunk.label(chunk.lines.joinToString(separator = "\n"))
    }
}
