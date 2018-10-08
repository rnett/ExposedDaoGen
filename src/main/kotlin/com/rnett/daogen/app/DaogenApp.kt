package com.rnett.daogen.app

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import com.rnett.daogen.ddl.Database
import com.rnett.daogen.ddl.Table
import com.rnett.daogen.ddl.generateKotlin
import javafx.event.Event
import javafx.event.EventType
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.control.*
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.Priority
import javafx.stage.FileChooser
import tornadofx.*
import java.io.File
import kotlin.properties.Delegates

val DB_CHANGED = EventType<DBChangeEvent>("DB_CHANGE")

class DBChangeEvent : Event(DB_CHANGED)

class DaogenApp : App(AppView::class) {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            launch<DaogenApp>(args)
        }
    }
}

class AppView : View() {

    val tablesList: ListView<String> by fxid()
    val objectTree: TreeView<*> by fxid()
    val classTree: TreeView<*> by fxid()
    val editorPane: AnchorPane by fxid()
    val editorLabel: Label by fxid()
    val codeArea: TextArea by fxid()
    val autosaveCheckbox: CheckBox by fxid()
    val autoExportCheckbox: CheckBox by fxid()

    override val root: AnchorPane by fxml()

    init {
        title = "Daogen: Untitled"
    }

    var db: Database? by Delegates.observable(null as Database?) { _, _, new ->

        tablesList.items.clear()
        objectTree.root = null
        classTree.root = null
        editorPane.children.clear()
        editorLabel.text = "No Item Selected"
        codeArea.text = ""

        if (new != null) {
            tables = new.tables.map { Pair(it.name, it) }.toMap()
        }
    }

    fun setEditor(edit: EditableItem) {
        editorPane.children.clear()
        edit.root.anchorpaneConstraints {
            bottomAnchor = 0
            topAnchor = 0
            leftAnchor = 0
            rightAnchor = 0
        }
        editorPane.children.add(edit.root)

        editorLabel.text = edit.name
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

    init {
        tablesList.selectionModel.selectedItemProperty().addListener { _, _, new ->
            objectRoot = ObjectItem(tables[new]!!)
            classRoot = ClassItem(tables[new]!!)
        }

        objectTree.selectionModel.selectedItemProperty().addListener { _, _, new ->
            if (new is HasItem) {
                setEditor(new.item)
            }
        }

        classTree.selectionModel.selectedItemProperty().addListener { _, _, new ->
            if (new is HasItem) {
                setEditor(new.item)
            }
        }
    }

    fun generateCode(): String {
        val t = db?.generateKotlin(exportFile?.path ?: "") ?: ""
        codeArea.text = t
        return t
    }

    var isSaveCurrent: Boolean by Delegates.observable(false) { _, _, new ->
        if (new)
            title = "Daogen: " + (saveFile?.nameWithoutExtension ?: "Untitled") + (exportFile?.name ?: "None")
        else
            title = "Daogen: *" + (saveFile?.nameWithoutExtension ?: "Untitled") + (exportFile?.name ?: "None")
    }

    init {
        root.addEventHandler(DB_CHANGED) {
            isSaveCurrent = false
            generateCode()
            if (autosaveCheckbox.isSelected) {
                if (saveFile != null)
                    save()
            }
            if (autoExportCheckbox.isSelected) {
                if (exportFile != null)
                    export()
            }
        }
    }

    fun saveDB(file: File) {
        file.writeText(Gson().toJson(DBSave(db!!.data, exportFile?.path ?: "")))
        isSaveCurrent = true
    }

    fun loadDB(file: File) {

        val json = Gson().fromJson<DBSave>(file.readText())

        db = json.db.create()

        if (json.exportFilePath != "") {
            exportFile = File(json.exportFilePath)
            autoExportCheckbox.isSelected = true
        }

        isSaveCurrent = true
        generateCode()
    }

    var saveFile: File? by Delegates.observable(null as File?) { _, _, new ->
        if (isSaveCurrent)
            title = "Daogen: " + (new?.nameWithoutExtension ?: "Untitled") + " => " + (exportFile?.name ?: "None")
        else
            title = "Daogen: *" + (new?.nameWithoutExtension ?: "Untitled") + " => " + (exportFile?.name ?: "None")
    }

    var exportFile: File? by Delegates.observable(null as File?) { _, _, new ->
        if (isSaveCurrent)
            title = "Daogen: " + (saveFile?.nameWithoutExtension ?: "Untitled") + " => " + (new?.name ?: "None")
        else
            title = "Daogen: *" + (saveFile?.nameWithoutExtension ?: "Untitled") + " => " + (new?.name ?: "None")
    }

    class NewModal : Fragment("New Daogen") {

        var connStr = ""

        val model = NewModalModel()

        override val root: Parent = vbox {
            hbox {
                alignment = Pos.CENTER
                label("JDBC Connection String:").hboxConstraints { marginRight = 10.0; marginLeft = 10.0 }
                textfield {
                    hboxConstraints { hGrow = Priority.ALWAYS; marginRight = 10.0 }
                    prefColumnCount = 70
                    textProperty().bindBidirectional(model.connStr)
                }
            }

            hbox {
                vboxConstraints { marginTop = 20.0 }
                alignment = Pos.CENTER
                button("Create").setOnAction {
                    close()
                }
            }

        }

        inner class NewModalModel : ItemViewModel<NewModal>(this@NewModal) {
            var connStr = bind(NewModal::connStr, true)
        }

    }


    fun newDB() {
        val modal = find<NewModal>()
        modal.openModal(block = true)
        val connStr = modal.connStr

        saveFile = null
        db = Database.fromConnection(connStr)
        isSaveCurrent = true
    }

    fun openSaved() {
        val file = chooseFile("Open", arrayOf(daogenFilter), mode = FileChooserMode.Single).firstOrNull()
        if (file != null) {
            saveFile = file
            loadDB(file)
            autosaveCheckbox.isSelected = true
        }
    }

    fun save() {
        saveFile.also {
            if (it == null)
                saveAs()
            else
                saveDB(it)
        }
    }

    fun saveAs() {
        val file = chooseFile("Save As", arrayOf(daogenFilter), mode = FileChooserMode.Save).firstOrNull()
        if (file != null) {
            saveFile = file
            save()
            autosaveCheckbox.isSelected = true
        }
    }

    companion object {
        val daogenFilter = FileChooser.ExtensionFilter("Daogen Database File", "*.daogen")
        val kotlinFilter = FileChooser.ExtensionFilter("Kotlin Source File", "*.kt")
    }

    fun exportCode(file: File) {
        val t = generateCode()
        if (!t.isBlank())
            file.writeText(t)
    }

    fun export() {
        exportFile.also {
            if (it == null)
                exportTo()
            else
                exportCode(it)
        }

    }

    fun exportTo() {
        val file = chooseFile("Export As", arrayOf(kotlinFilter), mode = FileChooserMode.Save).firstOrNull()
        if (file != null) {
            exportFile = file
            export()
            autoExportCheckbox.isSelected = true
        }
    }

    fun importFromCode(file: File) {
        val firstline = file.readLines().first()
        if (firstline.startsWith(exportStarter)) {
            val export = firstline.substringAfter(exportStarter)
            File(export).also {
                if (it.exists())
                    try {
                        loadDB(it)
                        exportFile = it
                    } catch (e: Exception) {
                        //TODO error handling
                    }
            }

        }
    }

    //TODO error handeling if I try to set to a bad name
    //TODO things don't update properly
    //TODO something in export file isn't working.  Name isn't getting updated properly on open

    fun importFrom() {
        val file = chooseFile("Import From", arrayOf(kotlinFilter), mode = FileChooserMode.Single).firstOrNull()
        if (file != null) {
            importFromCode(file)
        }
    }
}

val exportStarter = "// Made with Exposed DaoGen (https://github.com/rnett/ExposedDaoGen).  Exported from "

data class DBSave(val db: Database.Data, val exportFilePath: String)