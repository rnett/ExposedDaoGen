package com.rnett.daogen

import com.rnett.daogen.database.DB
import com.rnett.daogen.ddl.generateKotlin
import com.rnett.daogen.ddl.generateTables
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

var doDAO = true

/**
 * args: <connectionString> [-f outFile] [-s schema] [-nodao] [-cc] [-q]
 */
fun main(args: Array<String>) {

    if (args.isEmpty())
        throw IllegalArgumentException("Must provide database connection string")

    val dbString = args[0]

    val outFile = args["-f", { File(args[1]) }]

    val schema = args["-s"]

    if ("-nodao" in args)
        doDAO = false

    val out = DB.connect(dbString).generateTables(schema).generateKotlin()

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