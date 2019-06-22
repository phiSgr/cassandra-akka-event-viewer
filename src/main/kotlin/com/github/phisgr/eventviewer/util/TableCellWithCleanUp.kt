package com.github.phisgr.eventviewer.util

import javafx.scene.control.TableColumn
import javafx.util.Callback
import tornadofx.FX
import tornadofx.Scope
import tornadofx.SmartTableCell

class TableCellWithCleanUp<S, T>(
    scope: Scope = FX.defaultScope,
    owningColumn: TableColumn<S, T>
) : SmartTableCell<S, T>(scope, owningColumn) {
    private val cleanUps = mutableListOf<() -> Unit>()
    fun addCleanUp(cleanUp: () -> Unit) {
        cleanUps += cleanUp
    }

    override fun updateItem(item: T, empty: Boolean) {
        cleanUps.forEach { it() }
        cleanUps.clear()
        super.updateItem(item, empty)
    }
}


fun <S, T> TableColumn<S, T>.cellFormatWithCleanup(
    scope: Scope,
    formatter: TableCellWithCleanUp<S, T>.(T) -> Unit
) {
    properties["tornadofx.cellFormat"] = formatter
    cellFactory = Callback {
        TableCellWithCleanUp(scope, it)
    }
}
