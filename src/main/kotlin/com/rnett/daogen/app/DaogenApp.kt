package com.rnett.daogen.app

import com.rnett.daogen.database.DB
import com.rnett.daogen.ddl.Table
import com.rnett.daogen.ddl.generateTables
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.TextField
import javafx.scene.control.TreeView
import javafx.scene.layout.AnchorPane
import tornadofx.*
import kotlin.properties.Delegates


class DaogenApp : App(AppView::class) {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            launch<DaogenApp>(args)
        }
    }
}

class AppView : View() {
    override val root: AnchorPane by fxml()

    val tablesList: ListView<String> by fxid()
    val objectTree: TreeView<*> by fxid()
    val classTree: TreeView<*> by fxid()
    val editorPane: AnchorPane by fxid()
    val editorLabel: Label by fxid()
    val dbConStringTF: TextField by fxid()

    var connectionString
        get() = dbConStringTF.text
        set(v) {
            dbConStringTF.text = v
        }

    var classRoot
        get() = classTree.root
        set(v) {
            classTree.root = v
        }

    var objectRoot
        get() = objectTree.root
        set(v) {
            objectTree.root = v
        }

    var tables: Map<String, Table> by Delegates.observable(emptyMap()) { _, _, new ->
        tablesList.selectionModel.clearSelection()
        tablesList.items.clear()
        tablesList.items.addAll(new.values.map { it.name })
    }

    val selectedTable = if (tablesList.selectionModel.selectedIndices.count() > 0) tables[tablesList.selectionModel.selectedItem] else null

    @FXML
    fun initalize() {
        tablesList.selectionModel.selectedItemProperty().addListener { it, old, new ->
            //TODO doesn't work
            objectRoot = ObjectItem(tables[new]!!)
            classRoot = ClassItem(tables[new]!!)
        }
    }

    fun loadDB() {
        //TODO error reporting

        tables = DB.connect(connectionString).generateTables().map { Pair(it.name, it) }.toMap()
    }

}