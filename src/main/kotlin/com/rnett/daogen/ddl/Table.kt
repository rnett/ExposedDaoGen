package com.rnett.daogen.ddl

import com.cesarferreira.pluralize.pluralize
import com.cesarferreira.pluralize.singularize
import com.rnett.daogen.app.EditableItem
import com.rnett.daogen.database.DB
import com.rnett.daogen.doDAO
import javafx.scene.Parent
import tornadofx.*
import java.sql.JDBCType

data class PrimaryKey(val index: Int, val key: Column) {
    override fun toString(): String = key.name
}

val Iterable<PrimaryKey>.columns get() = this.map { it.key }
val Sequence<PrimaryKey>.columns get() = this.map { it.key }

fun String.toObjectName() = this.pluralize()
fun String.toClassName() = this.singularize()

private fun getPkString(pks: Set<PrimaryKey>): String {

    val list = pks.map { it.key.name }

    val sb1 = StringBuilder(list.first())

    for (i in 1..list.lastIndex) {
        sb1.append("\\\" << ${8 * i} | \\\"${list[i]}")
    }

    return sb1.toString()
}

private fun getPkIdString(pks: Set<PrimaryKey>): String {

    val list = pks.map { it.key.name }

    val sb1 = StringBuilder(list.first())

    for (i in 1..list.lastIndex) {
        sb1.append(" shl ${8 * i} or ${list[i]}")
    }

    return sb1.toString()
}

class Table(
        val name: String,
        val columns: Map<String, Column>,
        val primaryKeys: Set<PrimaryKey>,
        fKs: Set<ForigenKey> = emptySet(),
        rKs: Set<ForigenKey> = emptySet(),
        val database: Database
) : Seraliziable<Table, Database> {

    constructor(
            name: String,
            columns: Set<Column>,
            primaryKeys: Set<PrimaryKey>,
            database: Database,
            foreignKeys: Set<ForigenKey> = emptySet(),
            referencingKeys: Set<ForigenKey> = emptySet()
    ) : this(name, columns.groupBy { it.name }.mapValues { it.value.first() }, primaryKeys, foreignKeys, referencingKeys, database)

    private var _foreignKeys: Set<ForigenKey> = fKs
    private var _referencingKeys: Set<ForigenKey> = rKs

    val foreignKeys get() = _foreignKeys
    val referencingKeys get() = _referencingKeys

    fun setForeignKeys(fks: Set<ForigenKey>) {
        _foreignKeys = fks
    }

    fun setReferencingKeys(rks: Set<ForigenKey>) {
        _referencingKeys = rks
    }

    fun setKeys(allKeys: Set<ForigenKey>) {
        setForeignKeys(allKeys.filter { it.fromTable == this }.toSet())
        setReferencingKeys(allKeys.filter { it.toTable == this }.toSet())
    }

    //TODO use varchar as a key by hashcoding it?  What about on postgres side?
    enum class PKType(val valid: Boolean = true, val composite: Boolean = false) {
        Int, Long, Other(false), CompositeInt(composite = true), CompositeLong(composite = true), CompositeOther(false, true)
    }

    val pkType
        get() =
            if (primaryKeys.size > 1)
                when {
                    primaryKeys.all { it.key.type.type == Type.IntType } -> PKType.CompositeInt
                    primaryKeys.all { it.key.type.type == Type.IntType || it.key.type.type == Type.LongType } -> PKType.CompositeInt
                    else -> PKType.CompositeOther
                }
            else
                when {
                    primaryKeys.first().key.type.type == Type.IntType -> PKType.Int
                    primaryKeys.first().key.type.type == Type.LongType -> PKType.Int
                    else -> PKType.Other
                }

    val objectName = name.toObjectName()
    val className = name.toClassName()

    var objectDisplayName = objectName
    var classDisplayName = className

    override val data get() = Data(this)

    class Data(val name: String, val columns: List<Column>, val pks: Set<Pair<String, Int>>, val className: String, val objectName: String) : Seralizer<Table, Database> {
        constructor(table: Table) : this(table.name, table.columns.values.toList(), table.primaryKeys.map { Pair(it.key.name, it.index) }.toSet(), table.classDisplayName, table.objectDisplayName)

        override fun create(parent: Database): Table {
            val t = Table(name, columns.toSet(), pks.map { (key, idx) -> PrimaryKey(idx, columns.find { it.name == key }!!) }.toSet(), parent)
            t.classDisplayName = className
            t.objectDisplayName = objectName

            return t
        }

    }

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

        override val name: String = (if (isObject) "Object: " else "Class: ") + this@Table.toString()

        override val root: Parent = vbox {
            paddingTop = 20
            propTextBox("Object Name: ", model.displayName)
            propTextBox("Class Name: ", model.otherDisplayName)
        }
    }

    val blacklisted = mutableSetOf<TableElement>()

    fun toKotlin(tableNames: Set<String>) = "${makeForObject(tableNames)}${if (doDAO) "\n\n\n" + makeForClass(tableNames) else ""}"

    val badClassNames get() = (database.tables.flatMap { listOf(it.classDisplayName, it.objectDisplayName) } + columns.filter { it.value !in blacklisted }.map { it.value.classDisplayName }).toSet()
    val badObjectNames get() = (database.tables.flatMap { listOf(it.classDisplayName, it.objectDisplayName) } + columns.filter { it.value !in blacklisted }.map { it.value.objectDisplayName }).toSet()

    fun makeForObject(tableNames: Set<String>): String =
            buildString {

                append("object $objectDisplayName : ")
                when (pkType) {
                    PKType.Int -> "IntIdTable(\"$name\", \"${primaryKeys.first().key.name}\")"
                    PKType.Long -> "LongIdTable(\"$name\", \"${primaryKeys.first().key.name}\")"
                    PKType.CompositeInt -> "IntIdTable(\"$name\", \"${getPkString(primaryKeys)}\")"
                    PKType.CompositeLong -> "LongIdTable(\"$name\", \"${getPkString(primaryKeys)}\")"
                    else -> "Table(\"$name\")"
                }.let { append(it) }

                appendln(" {")
                appendln()

                appendln("\t// Database Columns\n")

                columns.values.filter { it !in blacklisted }.forEach {
                    append("\t")
                    append(it.makeForObject())

                    primaryKeys.find { pk -> pk.key == it }?.apply {
                        if (primaryKeys.count() > 1)
                            append(".primaryKey($index)")
                        else
                            append(".primaryKey()")
                    }
                    appendln()
                }

                if (foreignKeys.any { it !in blacklisted }) {
                    appendln("\n")

                    appendln("\t// Foreign/Imported Keys (One to Many)\n")

                    foreignKeys.filter { it !in blacklisted }.forEach {
                        append("\t")
                        appendln(it.makeForeignForObject())
                    }
                }

                if (referencingKeys.any { it !in blacklisted }) {
                    appendln("\n")

                    appendln("\t// Referencing/Exported Keys (One to Many)\n")

                    appendln("\t// ${referencingKeys.count { it !in blacklisted }} keys.  Not present in object")
                }

                appendln("}")
            }

    fun makeForClass(tableNames: Set<String>): String =
            buildString {

                if (pkType == PKType.Other || pkType == PKType.CompositeOther)
                    throw IllegalArgumentException("Can not (yet) make classes for non-Int or Long keyed or composite keyed tables")

                val keyType = when (pkType) {
                    PKType.Int -> "Int"
                    PKType.Long -> "Long"
                    PKType.CompositeInt -> "Int"
                    PKType.CompositeLong -> "Long"
                    else -> "<ERROR>"
                }

                appendln("class $classDisplayName(id: EntityID<$keyType>) : ${keyType}Entity(id) {")

                appendln("\tcompanion object : ${keyType}EntityClass<$classDisplayName>($objectDisplayName) {")

                append("\t\t")
                appendln("fun idFromPKs(" +
                        primaryKeys.filter { it.key !in blacklisted }.joinToString(", ") { "${it.key.name}: ${it.key.type.type.kotlinType}" } +
                        "): " +
                        (if (pkType == PKType.CompositeInt || pkType == PKType.Int) "Int" else "Long") +
                        " = " +
                        getPkIdString(primaryKeys.filter { it.key !in blacklisted }.toSet()))
                appendln()

                append("\t\t")
                appendln("fun findByPKs(" +
                        primaryKeys.filter { it.key !in blacklisted }.joinToString(", ") { "${it.key.name}: ${it.key.type.type.kotlinType}" } +
                        ") = findById(idFromPKs(" +
                        primaryKeys.filter { it.key !in blacklisted }.joinToString(", ") { it.key.name } +
                        "))")

                if (pkType.composite) {
                    appendln()

                    append("\t\t")
                    appendln("operator fun com.rnett.daogen.get(" +
                            primaryKeys.filter { it.key !in blacklisted }.joinToString(", ") { "${it.key.name}: ${it.key.type.type.kotlinType}" } +
                            ") = findByPKs(" +
                            primaryKeys.filter { it.key !in blacklisted }.joinToString(", ") { it.key.name } +
                            ")")
                    appendln()

                    append("\t\t")
                    appendln("fun new(" +
                            primaryKeys.filter { it.key !in blacklisted }.joinToString(", ") { "${it.key.name}: ${it.key.type.type.kotlinType}" } +
                            ", init: $classDisplayName.() -> Unit) = new(idFromPKs(" +
                            primaryKeys.filter { it.key !in blacklisted }.joinToString(", ") { it.key.name } +
                            "), init)")
                }

                appendln("\t}\n")

                appendln("\t// Database Columns\n")

                columns.values.filter { it !in blacklisted }.forEach {
                    append("\t")
                    appendln(it.makeForClass(objectDisplayName))
                }

                if (foreignKeys.any { it !in blacklisted }) {
                    appendln("\n")

                    appendln("\t// Foreign/Imported Keys (One to Many)\n")

                    foreignKeys.filter { it !in blacklisted }.forEach {
                        append("\t")
                        appendln(it.makeForeignForClass())
                    }
                }

                if (referencingKeys.any { it !in blacklisted }) {
                    appendln("\n")

                    appendln("\t// Referencing/Exported Keys (One to Many)\n")

                    referencingKeys.filter { it !in blacklisted }.forEach {
                        append("\t")
                        appendln(it.makeReferencingForClass())
                    }
                }

                appendln("\n")

                appendln("\t// Helper Methods\n")

                appendln("\toverride fun equals(other: Any?): Boolean {")
                appendln("\t\tif(other == null || other !is $classDisplayName)")
                appendln("\t\t\treturn false")
                appendln()
                appendln("\t\treturn ${primaryKeys.filter { it.key !in blacklisted }.map { it.key.name }.joinToString(" && ") { "$it == other.$it" }}")
                appendln("\t}")

                appendln("\n")

                appendln("\toverride fun hashCode(): Int = ${primaryKeys.first { it.key !in blacklisted }.key.name}")

                appendln("\n")

                if (columns.values.filter { it.isNameColumn }.size == 1)
                    appendln("\toverride fun toString() = ${columns.values.find { it.isNameColumn }!!.name}")

                appendln("}")

            }

    override fun toString(): String {
        return buildString {
            appendln(name + ":")

            appendln("\tPrimary Key(s): ${primaryKeys.filter { it.key !in blacklisted }.sortedBy { it.index }.joinToString(", ")}")
            appendln()

            columns.forEach {
                appendln("\t$it")
            }

            if (foreignKeys.isNotEmpty()) {
                appendln()
                appendln("\tForeign Key(s):")
                foreignKeys.forEach {
                    appendln("\t\t$it")
                }
            }

            if (referencingKeys.isNotEmpty()) {
                appendln()
                appendln("\tReferencing Key(s):")
                referencingKeys.forEach {
                    appendln("\t\t$it")
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Table) return false

        if (name != other.name) return false
        if (columns != other.columns) return false
        if (primaryKeys != other.primaryKeys) return false
        if (_foreignKeys != other._foreignKeys) return false
        if (_referencingKeys != other._referencingKeys) return false
        if (objectDisplayName != other.objectDisplayName) return false
        if (classDisplayName != other.classDisplayName) return false
        if (blacklisted != other.blacklisted) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + columns.hashCode()
        result = 31 * result + primaryKeys.hashCode()
        result = 31 * result + _foreignKeys.hashCode()
        result = 31 * result + _referencingKeys.hashCode()
        result = 31 * result + objectDisplayName.hashCode()
        result = 31 * result + classDisplayName.hashCode()
        result = 31 * result + blacklisted.hashCode()
        return result
    }


    companion object {
        operator fun invoke(name: String, database: Database): Table {
            val cols = DB.connection!!.metaData.getColumns(null, null, name, null).let {
                generateSequence {
                    if (it.next()) {

                        val colName = it.getString(4)
                        val data = it.getString(6)
                        val dataSize = it.getString(7)
                        val numberSize = it.getString(9)

                        val typeId = it.getInt(5)
                        val typeName = JDBCType.valueOf(typeId).toString().toLowerCase()

                        val type = when {
                            typeName == "integer" -> Type.IntType.withData()
                            typeName == "double" -> Type.FloatType.withData()
                            typeName == "varchar" && data == "text" -> Type.Text.withData()
                            typeName == "varchar" -> Type.Varchar.withData(dataSize)
                            typeName == "bigint" -> Type.LongType.withData()
                            typeName == "numeric" -> Type.Decimal.withData(dataSize, numberSize)
                            typeName == "char" -> Type.Char.withData()
                            typeName == "boolean" -> Type.Bool.withData()
                            typeName == "bit" -> Type.Bool.withData()
                            else -> Type.Unknown.withData()
                        }


                        val notNull = it.getString(18) == "NO"
                        val autoIncrement = it.getString(23) == "YES"

                        Column(colName, type, notNull, autoIncrement)


                    } else null
                }.filterNotNull().toList()  // must be inside the use() block
            }.groupBy({ it.name }, { it }).mapValues { it.value.first() }

            val pks = DB.connection!!.metaData.getPrimaryKeys(null, null, name).let {
                generateSequence {
                    if (it.next()) {
                        Pair(it.getString(4), it.getInt(5))
                    } else null
                }.filterNotNull().toList()  // must be inside the use() block
            }.map { PrimaryKey(it.second, cols[it.first]!!) }

            /*

            */

            return Table(name, cols.values.toSet(), pks.toSet(), database)
        }
    }
}

class Test(val i1: Int, val i2: Int) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Test) return false

        if (i1 != other.i1) return false
        if (i2 != other.i2) return false

        return true
    }

    override fun hashCode(): Int {
        var result = i1
        result = 31 * result + i2
        return result
    }
}