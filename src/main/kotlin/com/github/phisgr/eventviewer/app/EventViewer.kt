package com.github.phisgr.eventviewer.app

import com.github.phisgr.eventviewer.services.CassandraService
import com.github.phisgr.eventviewer.services.CassandraServiceImpl
import com.github.phisgr.eventviewer.util.TableConfigs
import com.github.phisgr.eventviewer.views.MainView
import com.typesafe.config.ConfigFactory
import io.github.config4k.extract
import javafx.scene.paint.Color
import tornadofx.*
import kotlin.reflect.KClass


class EventViewer : App(MainView::class, Styles::class) {
    val cassandra: CassandraService

    init {

        val config = ConfigFactory.load().getConfig("eventViewer")
        val contactPoints = config.getStringList("contactPoints")
        val tableConfig = config.extract<TableConfigs>()

        cassandra = CassandraServiceImpl(contactPoints)

        FX.dicontainer = object : DIContainer {
            @Suppress("unchecked_cast", "implicit_cast_to_any")
            override fun <T : Any> getInstance(type: KClass<T>): T = when (type) {
                CassandraService::class -> cassandra
                TableConfigs::class -> tableConfig
                else -> error("Not configured")
            } as T
        }

    }

    override fun stop() {
        super.stop()
        cassandra.close()
    }
}


class Styles : Stylesheet() {
    companion object {
        val nonEditable by cssclass()
        val del by cssclass()
        val mod by cssclass()
        val ins by cssclass()
        val scary by cssclass()
        val code by cssclass()
    }


    init {
        label {
            padding = box(5.0.px)
        }
        nonEditable {
            unsafe("-fx-control-inner-background", Color.gray(0.5))
        }
        del {
            backgroundColor = multi(Color.PALEVIOLETRED)
        }
        mod {
            backgroundColor = multi(Color.LIGHTYELLOW)
            child(text) {
                fill = Color.BLACK
            }
        }
        ins {
            backgroundColor = multi(Color.LIGHTGREEN)
        }
        scary {
            backgroundColor = multi(Color.RED)
            child(text) {
                fill = Color.WHITE
            }
            backgroundRadius = multi(box(Dimension(0.0, Dimension.LinearUnits.px)))
        }
        code {
            fontFamily = "monospaced"
        }
    }

}
