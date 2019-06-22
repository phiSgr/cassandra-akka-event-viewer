package com.github.phisgr.eventviewer.views

import com.github.phisgr.eventviewer.services.CassandraService
import com.github.phisgr.eventviewer.util.TableConfigs
import com.github.thomasnield.rxkotlinfx.toBinding
import com.github.thomasnield.rxkotlinfx.toObservable
import javafx.beans.property.SimpleLongProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import kotlinx.coroutines.*
import tornadofx.*
import javax.naming.ConfigurationException
import kotlin.coroutines.CoroutineContext

class MainView : View("Event Viewer"), CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main
    private val cassandraService: CassandraService by di()
    private val tableConfigs: TableConfigs by di()

    private val persistenceId = SimpleStringProperty("")
    private val from = SimpleLongProperty(1L)
    private val to = SimpleLongProperty(100L)

    private val tableProp = run {
        val firstConfig = tableConfigs.messages.firstOrNull() ?: tableConfigs.snapshots.firstOrNull()
        ?: throw ConfigurationException("There should be a config for table.")

        SimpleObjectProperty(firstConfig)
    }

    private val eventsView = EventsView(tableProp.toObservable())

    override val root = borderpane {
        setPrefSize(1920.0, 1080.0)

        top = borderpane {
            left = hbox {

                label("Event Class")
                text(observable = tableProp.toObservable().map { it.className }.toBinding())

                label("Table Name")
                combobox(tableProp, values = tableConfigs.messages + tableConfigs.snapshots) {
                    cellFormat { text = it.tableName }
                }

                label("Persistence ID")
                textfield(persistenceId) {
                    prefWidth = 450.0
                }
            }
            right = hbox {
                textfield(from) {
                    promptText = "from"
                    prefWidth = 80.0
                }
                label("-")
                textfield(to) {
                    promptText = "to"
                    prefWidth = 80.0
                }
                button("Load").action {
                    loadEvents()
                }
            }
        }
        center = eventsView.root
    }

    private var loading: Job? = null
    private fun loadEvents() {
        loading?.cancel()
        eventsView.l.clear()

        loading = launch {
            eventsView.load {
                val results = cassandraService.select(
                    scope = this@launch,
                    persistenceId = persistenceId.get(),
                    from = from.get(),
                    to = to.get(),
                    config = tableProp.get()
                )

                for (e in results) {
                    if (!isActive) break
                    eventsView.l.add(e)
                }
            }
        }
    }

}
