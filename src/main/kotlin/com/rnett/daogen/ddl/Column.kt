package com.rnett.daogen.ddl

import com.cesarferreira.pluralize.pluralize

data class Column(
        val name: String,
        val type: DataType,
        val notNull: Boolean,
        val autoIncrement: Boolean
) {
    fun makeForObject(): String = "val $name = ${type.getKotlin(name)}" +
            (if (!notNull) ".nullable()" else "") +
            if (autoIncrement) ".autoIncrement()" else ""

    fun makeForClass(objectName: String): String = "val $name by $objectName.$name"

    override fun toString(): String = "$name $type" +
            (if (notNull) " not null" else "") +
            if (autoIncrement) " auto increment" else ""

    val isNameColumn = name.toLowerCase().contains("name") && (type.type == Type.Varchar || type.type == Type.Text)

}

class ForigenKey(
        val fromTable: Table,
        val toTable: Table,

        val fromColumn: Column,
        val toColumn: Column
) {

    val fromTableName get() = fromTable.name
    val toTableName get() = toTable.name
    val fromColumnName get() = fromColumn.name
    val toColumnName get() = toColumn.name

    val nullable get() = !fromColumn.notNull

    private fun getRKName(badNames: Set<String>, useColName: Boolean): String =
            if (useColName)
                "${fromTableName}_$fromColumnName"
            else {
                val test = fromTableName
                        .removeSuffix("ID")
                        .removeSuffix("Id")
                        .removeSuffix("id")
                        .pluralize()

                if (test !in badNames) test else test + "_rk"
            }

    private fun getFKName(badNames: Set<String>): String {
        val test = fromColumnName
                .removeSuffix("ID")
                .removeSuffix("Id")
                .removeSuffix("id")

        return if (badNames.contains(test))
            "${test}_fk"
        else
            test
    }

    fun makeReferencingForClass(badNames: Set<String>, useColName: Boolean): String = "val ${getRKName(badNames, useColName)} by ${fromTableName.toClassName()} " +
            (if (nullable) "optionalReferrersOn" else "referrersOn") +
            " ${fromTableName.toObjectName()}.${getFKName(badNames)}"

    fun makeForeignForObject(badNames: Set<String>) = "val ${getFKName(badNames)} = " +
            (if (nullable) "optReference" else "reference") +
            "(\"$fromColumnName\", ${toTableName.toObjectName()})"

    fun makeForeignForClass(badNames: Set<String>) = "val ${getFKName(badNames)} by ${toTableName.toClassName()} " +
            (if (nullable) "optionalReferencedOn" else "referencedOn") +
            " ${fromTableName.toObjectName()}.${getFKName(badNames)}"

    override fun toString(): String = "$fromTableName.$fromColumnName refers to $toTableName.$toColumnName"


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ForigenKey) return false

        if (fromTable != other.fromTable) return false
        if (toTable != other.toTable) return false
        if (fromColumn != other.fromColumn) return false
        if (toColumn != other.toColumn) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fromTable.hashCode()
        result = 31 * result + toTable.hashCode()
        result = 31 * result + fromColumn.hashCode()
        result = 31 * result + toColumn.hashCode()
        return result
    }
}