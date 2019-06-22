package com.github.phisgr.eventviewer.views

import com.github.phisgr.eventviewer.app.Styles
import com.github.phisgr.eventviewer.services.CassandraService
import com.github.phisgr.eventviewer.util.ProtoWithSeqNr
import com.github.phisgr.eventviewer.util.TableConfig
import com.github.phisgr.eventviewer.util.cellFormatWithCleanup
import com.github.phisgr.eventviewer.util.json
import com.github.thomasnield.rxkotlinfx.toBinding
import com.github.thomasnield.rxkotlinfx.toNullableBinding
import com.github.thomasnield.rxkotlinfx.toObservable
import io.reactivex.Observable
import io.reactivex.functions.BiFunction
import io.reactivex.rxjavafx.schedulers.JavaFxScheduler
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections.observableArrayList
import javafx.collections.ObservableList
import javafx.collections.transformation.FilteredList
import javafx.scene.control.TableView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tornadofx.*
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Predicate
import javax.script.ScriptEngineManager
import javax.script.ScriptException
import kotlin.coroutines.CoroutineContext

class EventsView(configObs: Observable<TableConfig>) : View(), CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job

    private val cassandraService: CassandraService by di()

    val l: ObservableList<ProtoWithSeqNr<TableConfig>> = observableArrayList()

    private val scriptFactory = ScriptEngineManager().getEngineByExtension("kts").factory!!
    private val predicateScript = SimpleStringProperty("")
    private val predicateObs =
        Observable.combineLatest<String, TableConfig, Optional<Predicate<ProtoWithSeqNr<TableConfig>>>>(
            predicateScript.toObservable().debounce(50, TimeUnit.MILLISECONDS),
            configObs,
            BiFunction { script, config ->
                val engine = scriptFactory.scriptEngine
                        as org.jetbrains.kotlin.script.jsr223.KotlinJsr223JvmLocalScriptEngine

                try {
                    engine.eval(
                        """
                        |fun f(data: ${config.emptyMessage.javaClass.canonicalName}): Boolean {
                        |  return $script
                        |}
                        """.trimMargin()
                    )
                    Optional.of(Predicate { event: ProtoWithSeqNr<TableConfig> ->
                        engine.invokeFunction("f", event.data) as Boolean
                    })
                } catch (e: ScriptException) {
                    Optional.empty()
                }
            }
        ).observeOn(JavaFxScheduler.platform())

    private val filteredList = FilteredList(l).also {
        it.predicateProperty().bind(predicateObs.toNullableBinding())
    }

    private val isLoading = SimpleIntegerProperty(0)
    suspend fun load(block: suspend () -> Unit) {
        isLoading += 1
        try {
            block()
        } finally {
            isLoading -= 1
        }
    }

    override val root = borderpane {
        top = textfield(predicateScript) {
            addClass(Styles.code)
            promptText = "Filter with data.whatever..."
            bindClass(predicateObs.map {
                if (it.isPresent || predicateScript.get().isNullOrEmpty()) Optional.empty() else Optional.of(Styles.del)
            }.toNullableBinding())
        }

        center = tableview(filteredList) {
            readonlyColumn("Data", ProtoWithSeqNr<TableConfig>::data).cellFormat {
                text = it.json()
            }
            readonlyColumn("Seq No.", ProtoWithSeqNr<TableConfig>::seqNr) {
                maxWidth = 300.0
                cellFormatWithCleanup(scope) {
                    text = it.toString()
                    val classBinding = rowItem.modificationState.map { s ->
                        when (s) {
                            ProtoWithSeqNr.ModificationState.UNCHANGED -> Optional.empty()
                            ProtoWithSeqNr.ModificationState.DELETED -> Optional.of(Styles.del)
                            ProtoWithSeqNr.ModificationState.MODIFIED -> Optional.of(Styles.mod)
                        }
                    }.toNullableBinding()
                    val observableStyleClass = bindClass(classBinding)
                    this.addCleanUp {
                        observableStyleClass.listener.changed(classBinding, classBinding.value, null)
                        observableStyleClass.dispose()

                        classBinding.dispose()
                    }
                }
            }
            onDoubleClick {
                selectedItem?.let { selected ->
                    EventModifyView(selected).openModal()
                }
            }
            contextmenu {
                item("Delete").action {
                    launch {
                        val item = selectedItem!!
                        log.info { "Deleting seq no. ${item.seqNr} : ${item.data.json()}" }
                        load { cassandraService.delete(item) }
                        item.modificationState.onNext(ProtoWithSeqNr.ModificationState.DELETED)
                    }
                }
            }
            columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        }
        bottom = borderpane {
            left = text(l.sizeProperty.toObservable().map { "Loaded $it rows." }.toBinding())
            right = progressindicator {
                prefHeight = 12.0
                prefWidth = 12.0
                visibleWhen(isLoading.greaterThan(0))
            }
        }
    }
}
