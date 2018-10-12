package com.rnett.daogen.ddl

import com.cesarferreira.pluralize.pluralize
import com.rnett.daogen.app.EditableItem
import javafx.scene.Parent
import tornadofx.*

interface TableElement

data class Column(
        val name: String,
        val type: DataType,
        val notNull: Boolean,
        val autoIncrement: Boolean
) : TableElement {
    fun makeForObject(): String = "val $objectDisplayName = ${type.getKotlin(name)}" +
            (if (!notNull) ".nullable()" else "") +
            if (autoIncrement) ".autoIncrement()" else ""

    fun makeForClass(objectName: String): String = "${if (mutable) "var" else "val"} $classDisplayName by $objectName.$objectDisplayName"

    override fun toString(): String = "$classDisplayName $type" +
            (if (notNull) " not null" else "") +
            if (autoIncrement) " auto increment" else ""

    var classDisplayName: String = name
    var objectDisplayName: String = name
    var mutable: Boolean = false

    inner class Display(val isObject: Boolean) : EditableItem() {

        override var displayName
            get() = objectDisplayName
            set(v) {
                objectDisplayName = v
            }

        override var otherDisplayName
            get() = classDisplayName
            set(v) {
                classDisplayName = v
            }

        var mutable
            get() = this@Column.mutable
            set(v) {
                this@Column.mutable = v
            }

        override val name: String = "Column in " + (if (isObject) "Object: " else "Class: ") + this@Column.toString()

        val mutableModel = MutableModel()

        override val root: Parent = vbox {
            paddingTop = 20
            propTextBox("Object Property Name: ", model.displayName)
            propTextBox("Class Property Name: ", model.otherDisplayName)
            propCheckBox("Mutable: ", mutableModel.mutable)
        }

        inner class MutableModel : ItemViewModel<Display>(this@Display) {
            val mutable = bind(Display::mutable, true)
        }
    }

    val isNameColumn = ("name" in name.toLowerCase() ||
            "name" in classDisplayName.toLowerCase() ||
            "name" in objectDisplayName.toLowerCase())
            && (type.type == Type.Varchar || type.type == Type.Text)


    val classDisplay get() = Display(false)
    val objectDisplay get() = Display(true)
}

class ForigenKey(
        val fromTable: Table,
        val toTable: Table,

        val fromColumn: Column,
        val toColumn: Column
) : TableElement, Seraliziable<ForigenKey, Database> {

    var nullable = !fromColumn.notNull

    private fun getRKName(): String =
            if (toTable.referencingKeys.count { it.fromTable == fromTable } > 1)
                "${fromTable.classDisplayName}_${fromColumn.classDisplayName}"
            else {
                val test = fromTable.classDisplayName
                        .removeSuffix("ID")
                        .removeSuffix("Id")
                        .removeSuffix("id")
                        .pluralize()

                if (test !in toTable.badClassNames) test else test + "_rk"
            }

    private fun getFKName(badNames: Set<String>): String {
        val test = fromColumn.classDisplayName
                .removeSuffix("ID")
                .removeSuffix("Id")
                .removeSuffix("id")

        return if (badNames.contains(test))
            "${test}_fk"
        else
            test
    }

    var fkClassName = getFKName(fromTable.badClassNames)
    var fkObjectName = getFKName(fromTable.badObjectNames)
    var rkClassName = getRKName()

    var mutable: Boolean = false

    override val data get() = Data(this)

    class Data(
            val fromTable: String,
            val toTable: String,
            val fromColumn: String,
            val toColumn: String,
            val fkClassName: String,
            val fkObjectName: String,
            val rkClassName: String,
            val mutable: Boolean
    ) : Seralizer<ForigenKey, Database> {

        constructor(fk: ForigenKey) : this(
                fk.fromTable.name,
                fk.toTable.name,
                fk.fromColumn.name,
                fk.toColumn.name,
                fk.fkClassName,
                fk.fkObjectName,
                fk.rkClassName,
                fk.mutable
        )

        override fun create(parent: Database): ForigenKey {
            val fTable = parent.tables.find { it.name == fromTable }!!
            val tTable = parent.tables.find { it.name == toTable }!!

            val fk = ForigenKey(fTable, tTable, fTable.columns[fromColumn]!!, tTable.columns[toColumn]!!)

            fk.fkClassName = fkClassName
            fk.fkObjectName = fkObjectName
            fk.rkClassName = rkClassName
            fk.mutable = mutable

            return fk
        }

    }

    inner class FKDisplay internal constructor(val isObject: Boolean) : EditableItem() {
        override var displayName
            get() = fkObjectName
            set(v) {
                fkObjectName = v
            }
        override var otherDisplayName
            get() = fkClassName
            set(v) {
                fkClassName = v
            }

        var mutable
            get() = this@ForigenKey.mutable
            set(v) {
                this@ForigenKey.mutable = v
            }

        override val name: String = "Foreign Key in " + (if (isObject) "Object: " else "Class: ") + this@ForigenKey.toString()

        val mutableModel = MutableModel()

        override val root: Parent = vbox {
            paddingTop = 20
            propTextBox("Object Property Name: ", model.displayName)
            propTextBox("Class Property Name: ", model.otherDisplayName)
            propCheckBox("Mutable: ", mutableModel.mutable)
        }

        inner class MutableModel : ItemViewModel<FKDisplay>(this@FKDisplay) {
            val mutable = bind(FKDisplay::mutable, true)
        }
    }

    inner class RKDisplay internal constructor() : EditableItem() {
        override var displayName
            get() = rkClassName
            set(v) {
                rkClassName = v
            }

        override var otherDisplayName: String = ""

        override val name: String = "Referencing Key in Class: " + this@ForigenKey.toString()

        override val root: Parent = vbox {
            paddingTop = 20
            propTextBox("Class Property Name: ", model.displayName)
        }
    }

    //TODO mutable.  class fk only?

    fun makeReferencingForClass(): String = "val $rkClassName by ${fromTable.classDisplayName} " +
            (if (nullable) "optionalReferrersOn" else "referrersOn") +
            " ${fromTable.objectDisplayName}.$fkObjectName"

    fun makeForeignForObject() = "val $fkObjectName = " +
            (if (nullable) "optReference" else "reference") +
            "(\"${fromColumn.name}\", ${toTable.objectDisplayName})"

    fun makeForeignForClass() = "${if (mutable) "var" else "val"} $fkClassName by ${toTable.classDisplayName} " +
            (if (nullable) "optionalReferencedOn" else "referencedOn") +
            " ${fromTable.objectDisplayName}.$fkObjectName"

    override fun toString(): String = "${fromTable.name}.${fromColumn.name} refers to ${toTable.name}.${toColumn.name}"


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ForigenKey) return false

        if (fromTable.name != other.fromTable.name) return false
        if (toTable.name != other.toTable.name) return false
        if (fromColumn != other.fromColumn) return false
        if (toColumn != other.toColumn) return false
        if (nullable != other.nullable) return false
        if (fkClassName != other.fkClassName) return false
        if (fkObjectName != other.fkObjectName) return false
        if (rkClassName != other.rkClassName) return false
        if (mutable != other.mutable) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fromTable.name.hashCode()
        result = 31 * result + toTable.name.hashCode()
        result = 31 * result + fromColumn.hashCode()
        result = 31 * result + toColumn.hashCode()
        result = 31 * result + nullable.hashCode()
        result = 31 * result + fkClassName.hashCode()
        result = 31 * result + fkObjectName.hashCode()
        result = 31 * result + rkClassName.hashCode()
        result = 31 * result + mutable.hashCode()
        return result
    }

    val fkClassDisplay get() = FKDisplay(false)
    val fkObjectDisplay get() = FKDisplay(true)
    val rkDisplay get() = RKDisplay()

}