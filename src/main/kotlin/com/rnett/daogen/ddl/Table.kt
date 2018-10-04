package com.rnett.daogen.ddl

import com.cesarferreira.pluralize.pluralize
import com.cesarferreira.pluralize.singularize
import com.rnett.daogen.database.DB
import com.rnett.daogen.doDAO
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
        rKs: Set<ForigenKey> = emptySet()
) {

    constructor(
            name: String,
            columns: Set<Column>,
            primaryKeys: Set<PrimaryKey>,
            foreignKeys: Set<ForigenKey> = emptySet(),
            referencingKeys: Set<ForigenKey> = emptySet()
    ) : this(name, columns.groupBy { it.name }.mapValues { it.value.first() }, primaryKeys, foreignKeys, referencingKeys)

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

    fun toKotlin(tableNames: Set<String>) = "${makeForObject(tableNames)}${if (doDAO) "\n\n\n" + makeForClass(tableNames) else ""}"

    val badNames = columns.keys + setOf(className, objectName)

    fun makeForObject(tableNames: Set<String>): String =
            buildString {
                append("object $objectName : ")
                when (pkType) {
                    PKType.Int -> "IntIdTable(\"$name\", \"${primaryKeys.first().key.name}\")"
                    PKType.Long -> "LongIdTable(\"$name\", \"${primaryKeys.first().key.name}\")"
                    PKType.CompositeInt -> "IntIdTable(\"$name\", \"${getPkString(primaryKeys)}\")"
                    PKType.CompositeLong -> "LongIdTable(\"$name\", \"${getPkString(primaryKeys)}\")"
                    else -> "Table(\"$name\")"
                }.also { append(it) }

                appendln(" {")
                appendln()

                appendln("\t// Database Columns\n")

                columns.values.forEach {
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

                if (foreignKeys.isNotEmpty()) {
                    appendln("\n")

                    appendln("\t// Foreign/Imported Keys (One to Many)\n")

                    foreignKeys.forEach {
                        append("\t")
                        appendln(it.makeForeignForObject(badNames + tableNames))
                    }
                }

                if (referencingKeys.isNotEmpty()) {
                    appendln("\n")

                    appendln("\t// Referencing/Exported Keys (One to Many)\n")

                    appendln("\t// Not present in object")
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

                appendln("class $className(id: EntityID<$keyType>) : ${keyType}Entity(id) {")

                appendln("\tcompanion object : ${keyType}EntityClass<$className>($objectName) {")

                append("\t\t")
                appendln("fun idFromPKs(" +
                        primaryKeys.joinToString(", ") { "${it.key.name}: ${it.key.type.type.kotlinType}" } +
                        "): " +
                        (if (pkType == PKType.CompositeInt || pkType == PKType.Int) "Int" else "Long") +
                        " = " +
                        getPkIdString(primaryKeys))
                appendln()

                append("\t\t")
                appendln("fun findByPKs(" +
                        primaryKeys.joinToString(", ") { "${it.key.name}: ${it.key.type.type.kotlinType}" } +
                        ") = findById(idFromPKs(" +
                        primaryKeys.joinToString(", ") { it.key.name } +
                        "))")

                if (pkType.composite) {
                    appendln()

                    append("\t\t")
                    appendln("operator fun com.rnett.daogen.get(" +
                            primaryKeys.joinToString(", ") { "${it.key.name}: ${it.key.type.type.kotlinType}" } +
                            ") = findByPKs(" +
                            primaryKeys.joinToString(", ") { it.key.name } +
                            ")")
                    appendln()

                    append("\t\t")
                    appendln("fun new(" +
                            primaryKeys.joinToString(", ") { "${it.key.name}: ${it.key.type.type.kotlinType}" } +
                            ", init: $className.() -> Unit) = new(idFromPKs(" +
                            primaryKeys.joinToString(", ") { it.key.name } +
                            "), init)")
                }

                appendln("\t}\n")

                appendln("\t// Database Columns\n")

                columns.values.forEach {
                    append("\t")
                    appendln(it.makeForClass(objectName))
                }

                if (foreignKeys.isNotEmpty()) {
                    appendln("\n")

                    appendln("\t// Foreign/Imported Keys (One to Many)\n")

                    foreignKeys.forEach {
                        append("\t")
                        appendln(it.makeForeignForClass(badNames + tableNames))
                    }
                }

                if (referencingKeys.isNotEmpty()) {
                    appendln("\n")

                    appendln("\t// Referencing/Exported Keys (One to Many)\n")

                    referencingKeys.forEach {
                        append("\t")
                        appendln(it.makeReferencingForClass(badNames + tableNames,
                                referencingKeys.groupBy { it.fromTableName }[it.fromTableName]?.count() ?: 0 > 1))
                    }
                }

                appendln("\n")

                appendln("\t// Helper Methods\n")

                appendln("\toverride fun equals(other: Any?): Boolean {")
                appendln("\t\tif(other == null || other !is $className)")
                appendln("\t\t\treturn false")
                appendln()
                appendln("\t\treturn ${primaryKeys.map { it.key.name }.joinToString(" && ") { "$it == other.$it" }}")
                appendln("\t}")

                appendln("\n")

                appendln("\toverride fun hashCode(): Int = ${primaryKeys.first().key.name}")

                appendln("\n")

                if (columns.values.filter { it.isNameColumn }.size == 1)
                    columns.values.find { it.isNameColumn }!!.also {
                        appendln("\toverride fun toString() = ${it.name}")
                    }

                appendln("}")

            }

    override fun toString(): String {
        return buildString {
            appendln(name + ":")

            appendln("\tPrimary Key(s): ${primaryKeys.sortedBy { it.index }.joinToString(", ")}")
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

        return true
    }

    override fun hashCode(): Int = name.hashCode()


    companion object {
        operator fun invoke(name: String): Table {
            val cols = DB.connection!!.metaData.getColumns(null, null, name, null).use {
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

            val pks = DB.connection!!.metaData.getPrimaryKeys(null, null, name).use {
                generateSequence {
                    if (it.next()) {
                        Pair(it.getString(4), it.getInt(5))
                    } else null
                }.filterNotNull().toList()  // must be inside the use() block
            }.map { PrimaryKey(it.second, cols[it.first]!!) }

            /*

            */

            return Table(name, cols.values.toSet(), pks.toSet())
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