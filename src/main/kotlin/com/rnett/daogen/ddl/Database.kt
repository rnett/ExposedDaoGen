package com.rnett.daogen.ddl

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.rnett.daogen.app.exportStarter
import com.rnett.daogen.database.DB
import java.sql.Connection

data class Database(val schema: String?, val tables: MutableList<Table> = mutableListOf()) : Seraliziable<Database, Any?> {

    override val data get() = Data(this)

    class Data(val schema: String?, val tables: List<Table.Data>, val refs: List<ForigenKey.Data>) : Seralizer<Database, Any?> {

        constructor(db: Database) : this(db.schema, db.tables.map { it.data }, db.tables.flatMap { it.foreignKeys + it.referencingKeys }.toSet().map { it.data })

        fun create() = create(null)

        override fun create(parent: Any?): Database {
            val db = Database(schema)
            db.tables.addAll(tables.map { it.create(db) })

            val keys = refs.map { it.create(db) }.toSet()

            db.tables.forEach { it.setKeys(keys) }

            return db
        }
    }

    fun toJson(prettyPrint: Boolean = false) = if (!prettyPrint) Gson().toJson(data) else GsonBuilder().setPrettyPrinting().create().toJson(data)

    companion object {
        fun fromJson(json: String) = Gson().fromJson<Data>(json).create()

        operator fun get(json: String) = fromJson(json)

        fun fromConnection(connectionString: String, schema: String? = "public") = DB.connect(connectionString).generateDB(connectionString, schema)
        operator fun invoke(connectionString: String, schema: String? = "public") = fromConnection(connectionString, schema)
    }
}

fun Connection.generateDB(connectionString: String, schema: String? = "public"): Database {

    val db = Database(schema)

    val tableNames = DB.connection!!.metaData.getTables(null, schema, "%", arrayOf("TABLE")).let {
        generateSequence {
            if (it.next()) it.getString(3) else null
        }.filterNotNull().toList()  // must be inside the use() block
    }

    val tables = tableNames.map { Pair(it, Table(it, db)) }.toMap()

    val allKeys = tables.flatMap {
        DB.connection!!.metaData.getImportedKeys(null, null, it.key).let {
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

    tables.values.forEach { it.setKeys(allKeys) }

    db.tables.addAll(tables.values)

    return db
}

fun Database.generateKotlin(exportFilePath: String = "") = buildString {

    if (exportFilePath.isNotBlank())
        appendln(exportStarter + exportFilePath)

    appendln()

    appendln("""
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
""".trimIndent())
    appendln()

    val tableNames = this@generateKotlin.tables.flatMap { listOf(it.name, it.classDisplayName, it.objectDisplayName) }.toSet()

    this@generateKotlin.tables.forEach {
        try {
            appendln(it.toKotlin(tableNames))
        } catch (e: Exception) {
        }
    }
}