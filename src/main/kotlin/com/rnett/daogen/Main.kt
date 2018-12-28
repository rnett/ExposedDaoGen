package com.rnett.daogen

import com.rnett.daogen.database.DB
import com.rnett.daogen.ddl.*
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import kotlin.contracts.ExperimentalContracts

/**
 * args: <connectionString> [-p package] [-f outFile] [-s schema] [-tables <tablesCSVList>] [-nodao] [-noserialize] [-multiplatform <JVM/JS/Common>] [-cc] [-q]
 */
@ExperimentalContracts
fun main(args: Array<String>) {

    if (args.contains("--version")) {
        println("Daogen version 2.0.0")
        return
    }

    if (args.isEmpty())
        throw IllegalArgumentException("Must provide database connection string")

    val dbString = args[0]

    val outFile = args["-f", { File(args[1]) }]

    val schema = args["-s"]

    val pack = args["-p"] ?: "unknown"

    val tables = args["-tables"]

    val doDao = "-nodao" !in args
    val doSerialize = "-noserialize" !in args
    val multiplatform = args["-multiplatform"]

    val options = GenerationOptions(pack, doSerialize, true, doDao, multiplatform != null)

    val db = DB.connect(dbString).generateDB(dbString, schema, tables?.split(",")?.map { it.trim() })

    val out = when (multiplatform) {
        "JS" -> db.generateKotlinJS(options)
        "Common" -> db.generateKotlinCommon(options)
        else -> db.generateKotlin(options)
    }

    if ("-cc" in args) {
        val sel = StringSelection(out)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
    }

    outFile?.writeText(out)

    if ("-q" !in args)
        println(out)

    return
}


fun extract(flag: String, args: Array<String>): String? =
        args.indexOf(flag).let {
            if (it == -1 || it == args.lastIndex)
                null
            else
                args[it + 1]
        }

fun <T> extract(flag: String, args: Array<String>, func: (String) -> T) = extract(flag, args)?.let(func)

infix fun Array<String>.extract(flag: String) = extract(flag, this)
operator fun Array<String>.get(flag: String) = extract(flag, this)

fun <T> Array<String>.extract(flag: String, func: (String) -> T) = extract(flag, this, func)
operator fun <T> Array<String>.get(flag: String, func: (String) -> T) = extract(flag, this, func)