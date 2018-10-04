package com.rnett.daogen.ddl

import com.rnett.daogen.database.DB
import java.sql.Connection

fun Connection.generateTables(schema: String? = null): List<Table> {

    val tableNames = DB.connection!!.metaData.getTables(null, schema, "%", arrayOf("TABLE")).use {
        generateSequence {
            if (it.next()) it.getString(3) else null
        }.filterNotNull().toList()  // must be inside the use() block
    }

    val tables = tableNames.map { Pair(it, Table(it)) }.toMap()

    val allKeys = tables.flatMap {
        DB.connection!!.metaData.getImportedKeys(null, null, it.key).use {
            generateSequence {
                if (it.next()) {

                    val fromTable = tables[it.getString(7)]!!
                    val toTable = tables[it.getString(3)]!!

                    val fromColumnn = fromTable.columns[it.getString(8)]!!
                    val toColumnn = toTable.columns[it.getString(4)]!!

                    ForigenKey(fromTable, toTable,
                            fromColumnn, toColumnn)

                } else null
            }.filterNotNull().toList()  // must be inside the use() block
        }
    }.toSet()

    tables.mapValues { it.value.setKeys(allKeys) }

    return tables.values.toList()
}

fun Iterable<Table>.generateKotlin() = buildString {
    appendln("""
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
""".trimIndent())
    appendln()

    val tableNames = this@generateKotlin.flatMap { listOf(it.name, it.className, it.objectName) }.toSet()

    this@generateKotlin.forEach {
        try {
            appendln(it.toKotlin(tableNames))
        } catch (e: Exception) {
        }
    }
}