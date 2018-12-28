package com.rnett.daogen.ddl

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.rnett.daogen.app.exportStarter
import com.rnett.daogen.database.DB
import java.io.File
import java.sql.Connection
import kotlin.contracts.ExperimentalContracts

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

        fun fromConnection(connectionString: String, schema: String? = "public", useTables: List<String>? = null) = DB.connect(connectionString).generateDB(connectionString, schema, useTables)
        operator fun invoke(connectionString: String, schema: String? = "public", useTables: List<String>? = null) = fromConnection(connectionString, schema, useTables)
    }
}

fun Connection.generateDB(connectionString: String, schema: String? = "public", useTables: List<String>?): Database {

    val db = Database(schema)

    val tableNames = DB.connection!!.metaData.getTables(null, schema, "%", arrayOf("TABLE")).let {
        generateSequence {
            if (it.next()) it.getString(3) else null
        }.filterNotNull().toList()  // must be inside the use() block
    }

    val tables = tableNames.filter { useTables?.contains(it) ?: true }.map { Pair(it, Table(it, db)) }.toMap()

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

data class GenerationOptions(
        var outPackage: String,
        var serialization: Boolean = true,
        var serializationIncludeColumns: Boolean = true,
        var doDao: Boolean = true,
        var multiplatform: Boolean = true,
        var nullableByDefault: Boolean = false,
        var dataTransfer: Boolean = true,
        var requestClientQualifiedName: String = ""
) {
    val requestClientName get() = requestClientQualifiedName.substringAfterLast('.')
}

@ExperimentalContracts
fun Database.generateKotlin(options: GenerationOptions, exportFilePath: String = "") = buildString {

    if (exportFilePath.isNotBlank())
        appendln(exportStarter + exportFilePath)

    appendln("\npackage ${options.outPackage}\n")

    appendln()

    appendln("""
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.SizedIterable
import org.jetbrains.exposed.dao.*
""".trimIndent())
    if (options.serialization)
        appendln("""
import kotlinx.serialization.*
import kotlinx.serialization.internal.HexConverter
import kotlinx.serialization.internal.StringDescriptor
import kotlinx.serialization.internal.SerialClassDescImpl
    """.trimIndent())

    if (options.dataTransfer && options.requestClientQualifiedName.isNotBlank())
        appendln("import ${options.requestClientQualifiedName}")

    appendln()

    val tableNames = this@generateKotlin.tables.flatMap { listOf(it.name, it.classDisplayName, it.objectDisplayName) }.toSet()

    this@generateKotlin.tables.forEach {
        try {
            appendln(it.toKotlin(options))
        } catch (e: Exception) {
        }
    }
}

@ExperimentalContracts
fun Database.generateKotlinJS(options: GenerationOptions): String = buildString {

    appendln("\npackage ${options.outPackage}\n")

    appendln()

    if (options.serialization)
        appendln("""
import kotlinx.serialization.*
import kotlinx.serialization.internal.HexConverter
import kotlinx.serialization.internal.StringDescriptor
import kotlinx.serialization.internal.SerialClassDescImpl
    """.trimIndent())

    if (options.dataTransfer && options.requestClientQualifiedName.isNotBlank())
        appendln("import ${options.requestClientQualifiedName}")

    appendln()

    this@generateKotlinJS.tables.forEach {
        try {
            appendln(it.makeClassForJS(options))
        } catch (e: Exception) {
        }
    }
}

@ExperimentalContracts
fun Database.generateKotlinCommon(options: GenerationOptions): String = buildString {

    appendln("\npackage ${options.outPackage}\n")

    appendln()

    if (options.serialization)
        appendln("""
import kotlinx.serialization.*
import kotlinx.serialization.internal.HexConverter
import kotlinx.serialization.internal.StringDescriptor
import kotlinx.serialization.internal.SerialClassDescImpl
    """.trimIndent())

    if (options.dataTransfer && options.requestClientQualifiedName.isNotBlank())
        appendln("import ${options.requestClientQualifiedName}")

    appendln()

    this@generateKotlinCommon.tables.forEach {
        try {
            appendln(it.makeClassForCommon(options))
        } catch (e: Exception) {
        }
    }
}


@ExperimentalContracts
fun Database.generateToFileSystemMultiplatform(baseDir: String, options: GenerationOptions) {
    generateCommon(File(baseDir.trimEnd('/') + "/commonMain/kotlin/${options.outPackage.replace('.', '/')}"), options)
    generateJS(File(baseDir.trimEnd('/') + "/jsMain/kotlin/${options.outPackage.replace('.', '/')}"), options)
    generateJVM(File(baseDir.trimEnd('/') + "/jvmMain/kotlin/${options.outPackage.replace('.', '/')}"), options)
}

@ExperimentalContracts
fun Database.generateToFileSystemPureJVM(baseDir: String, options: GenerationOptions) {
    generateJVM(File(baseDir.trimEnd('/') + "/" + options.outPackage.replace('.', '/')), options)
}

@ExperimentalContracts
private fun Database.generateJVM(packageBase: File, options: GenerationOptions) {
    packageBase.mkdirs()
    tables.forEach {
        val out = File(packageBase.path.trimEnd('/') + "/${it.name}.kt")
        out.createNewFile()
        out.writeText(buildString {
            appendln("\npackage ${options.outPackage}\n")

            appendln()

            appendln("""
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.SizedIterable
import org.jetbrains.exposed.dao.*
""".trimIndent())

            if (options.serialization)
                appendln("""
import kotlinx.serialization.*
import kotlinx.serialization.internal.HexConverter
import kotlinx.serialization.internal.StringDescriptor
import kotlinx.serialization.internal.SerialClassDescImpl
    """.trimIndent())

            if (options.dataTransfer && options.requestClientQualifiedName.isNotBlank())
                appendln("import ${options.requestClientQualifiedName}")

            appendln()

            try {
                appendln(it.toKotlin(options))
            } catch (e: Exception) {
            }

        })
    }
}

@ExperimentalContracts
private fun Database.generateJS(packageBase: File, options: GenerationOptions) {
    packageBase.mkdirs()
    tables.forEach {
        val out = File(packageBase.path.trimEnd('/') + "/${it.name}.kt")
        out.createNewFile()
        out.writeText(buildString {
            appendln("\npackage ${options.outPackage}\n")

            appendln()

            if (options.serialization)
                appendln("""
import kotlinx.serialization.*
import kotlinx.serialization.internal.HexConverter
import kotlinx.serialization.internal.StringDescriptor
import kotlinx.serialization.internal.SerialClassDescImpl
    """.trimIndent())

            if (options.dataTransfer)
                appendln("import com.rnett.kframe.data.callEndpoint")

            if (options.dataTransfer && options.requestClientQualifiedName.isNotBlank())
                appendln("import ${options.requestClientQualifiedName}")

            appendln()

            try {
                appendln(it.makeClassForJS(options))
            } catch (e: Exception) {
            }

        })
    }
}

@ExperimentalContracts
private fun Database.generateCommon(packageBase: File, options: GenerationOptions) {
    packageBase.mkdirs()
    tables.forEach {
        val out = File(packageBase.path.trimEnd('/') + "/${it.name}.kt")
        out.createNewFile()
        out.writeText(buildString {
            appendln("\npackage ${options.outPackage}\n")

            appendln()

            if (options.serialization)
                appendln("""
import kotlinx.serialization.*
import kotlinx.serialization.internal.HexConverter
import kotlinx.serialization.internal.StringDescriptor
import kotlinx.serialization.internal.SerialClassDescImpl
    """.trimIndent())

            if (options.dataTransfer && options.requestClientQualifiedName.isNotBlank())
                appendln("import ${options.requestClientQualifiedName}")

            appendln()

            try {
                appendln(it.makeClassForCommon(options))
            } catch (e: Exception) {
            }

        })
    }
}

/*
    TODO imports:
        import com.rnett.kframe.data.EndpointManager
        import com.rnett.kframe.data.addEndpoint
 */