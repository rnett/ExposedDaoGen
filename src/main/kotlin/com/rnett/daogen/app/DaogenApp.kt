package com.rnett.daogen.app

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import com.rnett.daogen.ddl.*
import javafx.event.Event
import javafx.event.EventType
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.control.*
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.Priority
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.stage.FileChooser
import tornadofx.*
import java.io.File
import kotlin.contracts.ExperimentalContracts
import kotlin.properties.Delegates

//TODO checkboxes for nullable (only doable if server is nullable, may want to set un-nullable)  Do I want this?
//TODO better update handling
//TODO beter export change handling
//TODO error handeling if I try to set to a bad name
//TODO not auto-generating bad names
//TODO make sure I hit all bad names (other tables?)
//TODO save package statement on import
//TODO use relative path for db file in exports



val DB_CHANGED = EventType<DBChangeEvent>("DB_CHANGE")

class DBChangeEvent : Event(DB_CHANGED)

@ExperimentalContracts
class DaogenApp : App(AppView::class) {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            launch<DaogenApp>(args)
        }
    }
}

@ExperimentalContracts
class AppView : View() {

    val tablesList: ListView<String> by fxid()
    val objectTree: TreeView<*> by fxid()
    val classTree: TreeView<*> by fxid()
    val editorPane: AnchorPane by fxid()
    val editorLabel: Label by fxid()
    val codeAreaJVM: TextArea by fxid()
    val codeAreaJS: TextArea by fxid()
    val codeAreaCommon: TextArea by fxid()
    val codeTabs: TabPane by fxid()
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
        codeAreaJVM.text = ""
        codeAreaJS.text = ""
        codeAreaCommon.text = ""

        if (new != null) {
            tables = new.tables.map { Pair(it.name, it) }.toMap()
        }
    }

    var generationOptions = GenerationOptions("unknown")

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
            if (!new.isNullOrBlank()) {
                objectRoot = ObjectItem(tables[new]!!)
                classRoot = ClassItem(tables[new]!!)
            }
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
        val t = db?.generateKotlin(generationOptions, saveFile?.path ?: "") ?: ""
        codeAreaJVM.text = t
        codeAreaJS.text = db?.generateKotlinJS(generationOptions) ?: ""
        codeAreaCommon.text = db?.generateKotlinCommon(generationOptions) ?: ""
        return t
    }

    var isSaveCurrent: Boolean by Delegates.observable(false) { _, _, new ->
        if (new)
            title = "Daogen: " + (saveFile?.nameWithoutExtension ?: "Untitled") + " ==> " + (exportFile?.name ?: "None")
        else
            title = "Daogen: *" + (saveFile?.nameWithoutExtension ?: "Untitled") + " ==> " + (exportFile?.name
                    ?: "None")
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

        this.primaryStage.isMaximized = true
    }

    fun saveDB(file: File) {
        file.writeText(Gson().toJson(DBSave(db!!.data, exportFile?.path ?: "", generationOptions)))
        isSaveCurrent = true
    }

    fun loadDB(file: File) {

        val json = Gson().fromJson<DBSave>(file.readText())

        db = json.db.create()

        generationOptions = json.generationOptions

        if (json.exportFilePath != "") {
            exportFile = File(json.exportFilePath)
            autoExportCheckbox.isSelected = true
        }

        isSaveCurrent = true
        generateCode()
    }

    var saveFile: File? by Delegates.observable(null as File?) { _, _, new ->
        title = if (isSaveCurrent)
            "Daogen: " + (new?.nameWithoutExtension ?: "Untitled") + " ==> " + (exportFile?.name ?: "None")
        else
            "Daogen: *" + (new?.nameWithoutExtension ?: "Untitled") + " ==> " + (exportFile?.name ?: "None")
    }

    var exportFile: File? by Delegates.observable(null as File?) { _, _, new ->

        if (new != null) {
            Regex("package ([A-z.0-9]*)\n").find(new.readText())?.groupValues?.getOrNull(1)?.let {
                generationOptions.outPackage = it
            }
        }

        title = if (isSaveCurrent)
            "Daogen: " + (saveFile?.nameWithoutExtension ?: "Untitled") + " ==> " + (new?.name ?: "None")
        else
            "Daogen: *" + (saveFile?.nameWithoutExtension ?: "Untitled") + " ==> " + (new?.name ?: "None")
    }

    class NewModal : Fragment("New Daogen") {

        var connStr = ""
        var schema = ""
        var useTables = ""

        val model = NewModalModel()

        override val root: Parent = vbox {
            hbox {
                vboxConstraints { marginTop = 10.0 }
                alignment = Pos.CENTER
                label("JDBC Connection String:").hboxConstraints { marginRight = 10.0; marginLeft = 10.0 }
                textfield {
                    hboxConstraints { hGrow = Priority.ALWAYS; marginRight = 10.0 }
                    prefColumnCount = 70
                    textProperty().bindBidirectional(model.connStr)
                }
            }
            hbox {
                vboxConstraints { marginTop = 10.0 }
                alignment = Pos.CENTER
                label("Schema:").hboxConstraints { marginRight = 10.0; marginLeft = 10.0 }
                textfield {
                    hboxConstraints { marginRight = 10.0 }
                    prefColumnCount = 30
                    textProperty().bindBidirectional(model.schema)
                }
            }
            hbox {
                vboxConstraints { marginTop = 10.0 }
                alignment = Pos.CENTER
                label("Use Tables:").hboxConstraints { marginRight = 10.0; marginLeft = 10.0 }
                textfield {
                    hboxConstraints { hGrow = Priority.ALWAYS; marginRight = 10.0 }
                    prefColumnCount = 70
                    textProperty().bindBidirectional(model.useTables)
                }
            }

            hbox {
                vboxConstraints { marginTop = 10.0 }
                alignment = Pos.CENTER
                button("Create").setOnAction {
                    close()
                }
            }

        }

        inner class NewModalModel : ItemViewModel<NewModal>(this@NewModal) {
            var connStr = bind(NewModal::connStr, true)
            var schema = bind(NewModal::schema, true)
            var useTables = bind(NewModal::useTables, true)
        }

    }

    class RefreshModal : Fragment("Refresh from DB") {

        var connStr = ""

        val model = RefreshModalModel()

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
                label("Warning: This will overwrite any changes you have made") {
                    textFill = Color.RED
                    font = Font.font(font.family, FontWeight.BOLD, 14.0)
                }
            }

            hbox {
                vboxConstraints { marginTop = 20.0 }
                alignment = Pos.CENTER
                button("Refresh").setOnAction {
                    close()
                }
            }

        }

        inner class RefreshModalModel : ItemViewModel<RefreshModal>(this@RefreshModal) {
            var connStr = bind(RefreshModal::connStr, true)
        }

    }

    class OptionsModal : Fragment("Options") {

        var packageName = ""
        var serialization = true
        var doDao = true
        var multiplatform = true
        var nullableByDefault = true

        fun setOptions(options: GenerationOptions) {
            model.packageName.value = options.outPackage
            model.serialization.value = options.serialization
            model.doDao.value = options.doDao
            model.multiplatform.value = options.multiplatform
            model.nullableByDefault.value = options.nullableByDefault
        }

        val model = OptionsModalModel()

        override val root: Parent = vbox {
            hbox {
                vboxConstraints { marginBottom = 10.0 }
                alignment = Pos.CENTER
                label("Package:").hboxConstraints { marginRight = 10.0; marginLeft = 10.0 }
                textfield {
                    hboxConstraints { marginRight = 10.0 }
                    prefColumnCount = 30
                    textProperty().bindBidirectional(model.packageName)
                }
            }
            hbox {
                vboxConstraints { marginBottom = 10.0 }
                alignment = Pos.CENTER
                label("Use nullable types by default:") {
                    hboxConstraints { marginRight = 10.0 }
                }
                checkbox {
                    selectedProperty().bindBidirectional(model.nullableByDefault)
                    selectedProperty().onChange {
                        fireEvent(DBChangeEvent())
                    }
                }
            }
            hbox {
                vboxConstraints { marginBottom = 10.0 }
                alignment = Pos.CENTER
                label("Generate KotlinX Serializer:") {
                    hboxConstraints { marginRight = 10.0 }
                }
                checkbox {
                    selectedProperty().bindBidirectional(model.serialization)
                    selectedProperty().onChange {
                        fireEvent(DBChangeEvent())
                    }
                }
            }
            hbox {
                vboxConstraints { marginBottom = 10.0 }
                alignment = Pos.CENTER
                label("Generate DAO:") {
                    hboxConstraints { marginRight = 10.0 }
                }
                checkbox {
                    selectedProperty().bindBidirectional(model.doDao)
                    selectedProperty().onChange {
                        fireEvent(DBChangeEvent())
                    }
                }
            }
            hbox {
                vboxConstraints { marginBottom = 10.0 }
                alignment = Pos.CENTER
                label("Multiplatform:") {
                    hboxConstraints { marginRight = 10.0 }
                }
                checkbox {
                    selectedProperty().bindBidirectional(model.multiplatform)
                    selectedProperty().onChange {
                        fireEvent(DBChangeEvent())
                    }
                }
            }

            hbox {
                alignment = Pos.CENTER
                button("Done").setOnAction {
                    close()
                }
            }

        }

        inner class OptionsModalModel : ItemViewModel<OptionsModal>(this@OptionsModal) {
            var packageName = bind(OptionsModal::packageName, true)
            var serialization = bind(OptionsModal::serialization, true)
            var doDao = bind(OptionsModal::doDao, true)
            var multiplatform = bind(OptionsModal::multiplatform, true)
            var nullableByDefault = bind(OptionsModal::nullableByDefault, true)

        }

    }

    class ExportFSModal : Fragment("Export to structured files") {

        var text1 = ""
        var text2 = ""

        var baseDir = ""

        val model = ExportFSModalModel()

        override val root: Parent = vbox {

            hbox {
                vboxConstraints { marginTop = 10.0 }
                alignment = Pos.CENTER
                label(model.text1) {
                    font = Font.font(font.family, FontWeight.BOLD, 14.0)
                    hboxConstraints { marginLeft = 20.0; marginRight = 20.0 }
                    isWrapText = true
                    alignment = Pos.CENTER
                }
            }
            hbox {
                vboxConstraints { marginTop = 5.0 }
                alignment = Pos.CENTER
                label(model.text2) {
                    font = Font.font(font.family, FontWeight.BOLD, 14.0)
                    hboxConstraints { marginLeft = 20.0; marginRight = 20.0 }
                    isWrapText = true
                    alignment = Pos.CENTER
                }
            }

            hbox {
                vboxConstraints { marginTop = 10.0 }
                alignment = Pos.CENTER
                button("Choose").setOnAction {
                    val file = chooseDirectory("Base Directory")
                    if (file != null)
                        model.baseDir.value = file.absolutePath
                }
            }

            hbox {
                vboxConstraints { marginTop = 10.0 }
                alignment = Pos.CENTER
                label("Currently Selected:") {
                    font = Font.font(font.family, FontWeight.BOLD, 14.0)
                    hboxConstraints { hGrow = Priority.ALWAYS; marginLeft = 20.0; marginRight = 20.0 }
                    isWrapText = true
                    alignment = Pos.CENTER
                }
            }

            hbox {
                vboxConstraints { marginTop = 5.0 }
                alignment = Pos.CENTER
                label(model.baseDir) {
                    font = Font.font(font.family, FontWeight.BOLD, 14.0)
                    hboxConstraints { hGrow = Priority.ALWAYS; marginLeft = 20.0; marginRight = 20.0 }
                    isWrapText = true
                    alignment = Pos.CENTER
                }
            }

            hbox {
                vboxConstraints { marginTop = 10.0 }
                alignment = Pos.CENTER
                button("Export").setOnAction {
                    close()
                }
            }

        }

        inner class ExportFSModalModel : ItemViewModel<ExportFSModal>(this@ExportFSModal) {
            var baseDir = bind(ExportFSModal::baseDir, true)
            var text1 = bind(ExportFSModal::text1, true)
            var text2 = bind(ExportFSModal::text2, true)
        }

    }

    fun exportFiles() {
        val modal = find<AppView.ExportFSModal> {
            if (generationOptions.multiplatform) {
                model.text1.value = "Choose the directory that contains the platform modules (commonMain, jvmMain, etc)."
                model.text2.value = "(\$projectDir/src for most InteliJ projects)"
            } else {
                model.text1.value = "Choose the directory that contains the first packages."
                model.text2.value = "(\$projectDir/src/main/kotlin for most InteliJ projects)"
            }
        }

        modal.openModal(block = true)

        val baseDir = modal.baseDir

        if (generationOptions.multiplatform)
            db?.generateToFileSystemMultiplatform(baseDir, generationOptions)
        else
            db?.generateToFileSystemPureJVM(baseDir, generationOptions)

    }

    fun options() {
        val modal = find<OptionsModal>()

        modal.setOptions(generationOptions)

        modal.openModal(block = true)

        generationOptions.outPackage = modal.packageName
        generationOptions.serialization = modal.serialization
        generationOptions.doDao = modal.doDao
        generationOptions.multiplatform = modal.multiplatform
        generationOptions.nullableByDefault = modal.nullableByDefault

        if (!generationOptions.multiplatform)
            codeTabs.selectionModel.select(0)

        codeTabs.tabs[1].isDisable = !generationOptions.multiplatform
        codeTabs.tabs[2].isDisable = !generationOptions.multiplatform

        generateCode()

    }


    fun newDB() {
        val modal = find<NewModal>()
        modal.openModal(block = true)
        val connStr = modal.connStr
        val schema = modal.schema.let { if (it.isBlank()) null else it }
        val useTables = if (modal.useTables.isBlank()) null else modal.useTables.split(",").map { it.trim() }

        saveFile = null
        exportFile = null
        db = Database.fromConnection(connStr, schema, useTables)
        isSaveCurrent = true

        generateCode()
    }

    fun fromDB() {
        val modal = find<RefreshModal>()
        modal.openModal(block = true)
        val connStr = modal.connStr

        db = Database.fromConnection(connStr)
        isSaveCurrent = true

        generateCode()

        if (saveFile != null)
            save()

        if (exportFile != null)
            export()
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

                        saveFile = it
                        exportFile = file

                        autosaveCheckbox.isSelected = true
                        autoExportCheckbox.isSelected = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        //TODO error handling
                    }
            }

        }
    }

    fun importFrom() {
        val file = chooseFile("Import From", arrayOf(kotlinFilter), mode = FileChooserMode.Single).firstOrNull()
        if (file != null) {
            importFromCode(file)
        }
    }
}

val exportStarter = "// Made with Exposed DaoGen (https://github.com/rnett/ExposedDaoGen).  Exported from "

data class DBSave(val db: Database.Data, val exportFilePath: String, val generationOptions: GenerationOptions)