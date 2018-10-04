package com.rnett.daogen.ddl

enum class Type(val database: String, val kotlin: String, val kotlinType: String, val params: Int = 0) {
    //TODO support for type name aliases, e.g. real and float
    IntType("int", "integer", "Int"),
    LongType("bigint", "long", "Long"),
    FloatType("float", "float", "Float"),
    DoubleType("double precision", "double", "Double"),
    Decimal("decimal(\$1, \$2)", "decimal(\$name, \$1, \$2)", "BigDecimal", 2),//TODO w/ params
    Bool("boolean", "bool", "Boolean"),
    Char("char", "char", "Char"),
    Varchar("varchar(\$1)", "varchar(\$name, \$1)", "String", 1),//TODO w/ param
    Text("text", "text", "String"),
    Unknown("", "//TODO unknown type", "String")
    //TODO more advanced types
    ;

    fun getKotlinFor(name: String, db: String = ""): String {
        if (db.isBlank() && params > 0)
            throw IllegalArgumentException("Must give DB text for types with parameters")

        if (params == 0)
            return if (kotlin.contains("\$name")) kotlin.replace("\$name", "\"$name\"") else "$kotlin(\"$name\")"
        else {
            val regex = database.replace("(", "\\(").replace(")", "\\)").replace("[\$][0-9]".toRegex(), "([0-9]*)")
            val matches = regex.toRegex().find(db)
                    ?: throw IllegalArgumentException("Database string must contain the type")

            var kotlinString = kotlin

            for (i in 1..params) {
                kotlinString = kotlinString.replace("\$$i", matches.groupValues[i])
            }

            kotlinString = kotlinString.replace("\$name", "\"$name\"")

            return kotlinString
        }
    }

    fun getKotlinFor(name: String, vararg args: String): String {
        if (args.size < params)
            throw IllegalArgumentException("Must give $params arguments")

        if (params == 0)
            return if (kotlin.contains("\$name")) kotlin.replace("\$name", "\"$name\"") else "$kotlin(\"$name\")"
        else {

            var kotlinString = kotlin

            for (i in 1..params) {
                kotlinString = kotlinString.replace("\$$i", args[i - 1])
            }

            kotlinString = kotlinString.replace("\$name", "\"$name\"")

            return kotlinString
        }
    }

    fun getDatabaseFor(vararg args: String): String {
        if (args.size < params)
            throw IllegalArgumentException("Must give $params arguments")

        if (params == 0)
            return database
        else {

            var dbString = database

            for (i in 1..params) {
                dbString = dbString.replace("\$$i", args[i - 1])
            }

            return dbString
        }
    }

    fun withData(vararg data: String) = DataType(this, *data)
}

data class DataType(val type: Type, val args: List<String>) {
    constructor(type: Type, vararg args: String) : this(type, args.toList())

    fun getKotlin(name: String) = type.getKotlinFor(name, *args.toTypedArray())

    override fun toString(): String = type.getDatabaseFor(*args.toTypedArray())
}

fun main(args: Array<String>) {
    println(Type.IntType.getKotlinFor("test"))
    println(Type.Varchar.getKotlinFor("test2", "varchar(100)"))
    println(Type.Decimal.getKotlinFor("test3", "decimal(20, 10)"))
}

