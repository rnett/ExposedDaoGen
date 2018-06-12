import com.cesarferreira.pluralize.pluralize
import com.cesarferreira.pluralize.singularize
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

fun main(args: Array<String>) {
    var text: String

    if (args.size > 0) // load file
        text = File(args[0]).readText()
    else // read from stdin
        text = System.`in`.bufferedReader().readText()

    // replace newlines on windows files
    text = text.replace("\r\n", "\n")

    val db = DaoDB(text)

    //println(db)

    // add imports
    var out = """import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.dao.EntityID


"""

    // output the thing
    out += db.makeKotlin()

    // copy to clipboard
    val sel = StringSelection(out)
    Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)

    // output
    println(out)

}


class DaoDB(createStatements: String) {
    val tables: List<DaoTable>

    init {
        tables = createStatements.split(";").filter { it.trim().startsWith("create table", true) }
                .map { DaoTable.makeFromDef(it) }

        tables.flatMap { it.manyToOne }.forEach { key ->
            tables.filter { it.objectName == key.referenceObject && it.className == key.referenceClass }.forEach {
                it.addReferencingKey(key)
            }
        }

    }

    fun makeKotlin(): String{
        return tables.joinToString("\n\n\n") { it.makeKotlin() }
    }

    override fun toString(): String {
        return tables.joinToString("\n\n\n") { it.displayString() }
    }

}

class DaoTable(val objectName: String, val className: String, columns: List<DaoColumn>) {
    val primaryKeys: List<DataColumn>
    val dataColumns: List<DataColumn>
    val foreignKeys: MutableList<ForigenKey>

    init{
        dataColumns = columns.filter { it is DataColumn }.map { it as DataColumn }
        foreignKeys = columns.filter { it is ForigenKey }.map { it as ForigenKey }.toMutableList()

        primaryKeys = columns.filter { it is PrimaryKey }.map { it as PrimaryKey }.flatMap { it.fields }.map {
            val pKeyName = it
            dataColumns.filter { it.name == pKeyName }.first()
        }
    }

    val manyToOne: List<ForigenKey> by lazy { foreignKeys.filter { !it.many } }
    val oneToMany: List<ForigenKey> by lazy { foreignKeys.filter { it.many } }

    fun addReferencingKey(rk: ForigenKey) {
        foreignKeys.add(ForigenKey(
                "${className}_${rk.className.pluralize()}",
                "${rk.className}",
                "${rk.objectName}",
                "${rk.referenceColumn}",
                true,
                objectName, className
        ))
    }

    override fun toString(): String {
        return displayString()
    }

    fun displayString(): String {
        val sb = StringBuilder()
        sb.append("$objectName/$className : ${primaryKeys.joinToString(", ") { it.name }}\n")

        dataColumns.joinTo(sb, "\n") { "\t${it.displayString()}" }

        if (oneToMany.count() > 0)
            sb.append("\n\n\tOne to Many:\n")
        oneToMany.joinTo(sb, "\n") { "\t${it.displayString()}" }

        if (manyToOne.count() > 0)
            sb.append("\n\n\tMany to One:\n")
        manyToOne.joinTo(sb, "\n") { "\t${it.displayString()}" }

        sb.append("\n")

        return sb.toString()
    }

    fun objectString(): String {

        // build primary key string
        val keyStr: String
        if(primaryKeys.size <= 1)
            keyStr = primaryKeys.first().name
        else{
            val list = primaryKeys.toList().map { it.name }
            val sb1 = StringBuilder(list.first())

            for(i in 1..list.size-1){
                sb1.append("\\\" << ${8*i} | \\\"${list[i]}")
            }

            keyStr = sb1.toString()
        }


        // definition and constructor
        val sb = StringBuilder("object $objectName: IntIdTable(columnName = \"$keyStr\") {\n")


        // columns
        sb.appendln("\n\t\\\\ Database columns\n\n")

        dataColumns.joinTo(sb, "\n") { "\t${it.objectString()}" }

        sb.append("\n\n\n")


        // foreign keys
        sb.appendln("\t\\\\ Foreign keys\n")

        if (manyToOne.count() > 0) {
            sb.appendln("\t\\\\ Many to One")

            manyToOne.joinTo(sb, "\n") { "\t" + it.objectString() }

            sb.append("\n")
        }

        if (manyToOne.count() > 0 && oneToMany.count() > 0) {
            sb.appendln()
        }

        if (oneToMany.count() > 0) {
            sb.appendln("\t\\\\ One to Many (not present in object)")

            sb.append("\n")

        }

        sb.append("\n")

        sb.appendln("\t\\\\ Helper methods\n")


        // idFromPKs(): clustered primary key helper function
        if(primaryKeys.size > 1) {
            sb.append("\tfun idFromPKs(${primaryKeys.joinToString(", ") { it.name + ": Int" }}): Int {\n")
            val list = primaryKeys.toList().map { it.name }
            val sb1 = StringBuilder(list.first())

            for(i in 1..list.size-1){
                sb1.append(" shl ${8*i} or ${list[i]}")
            }

            sb.append("\t\treturn $sb1\n\t}\n")
        }


        // findFromPKs(): there to help with clustered primary keys, put in everything for consistency
        if(primaryKeys.size <= 1){
            sb.append("\tfun findFromPKs(${primaryKeys.joinToString(", ") { it.name + ": Int" }}): ${className}? {\n")
            sb.append("\t\treturn ${className}.findById(${primaryKeys.joinToString(", ")})\n\t}\n")

        } else {
            sb.append("\tfun findFromPKs(${primaryKeys.joinToString(", ") { it.name + ": Int" }}): ${className}? {\n")
            sb.append("\t\treturn ${className}.findById(idFromPKs(${primaryKeys.joinToString(", ")}))\n\t}\n")
        }


        sb.append("\n}")

        return sb.toString()
    }

    fun classString(): String {

        val sb = StringBuilder()

        sb.append("class ${className}(id: EntityID<Int>): IntEntity(id) {\n")

        sb.append("\tcompanion object: IntEntityClass<${className}>($objectName)\n\n")


        // fields
        sb.appendln("\t\\\\ Database columns\n")

        dataColumns.joinTo(sb, "\n") { "\t" + it.classString() }

        sb.append("\n\n\n")


        sb.appendln("\t\\\\ Foreign keys\n")

        if (manyToOne.count() > 0) {
            sb.appendln("\t\\\\ Many to One")

            manyToOne.joinTo(sb, "\n") { "\t" + it.classString() }
            sb.append("\n")
        }

        if (manyToOne.count() > 0 && oneToMany.count() > 0) {
            sb.appendln()
        }

        if (oneToMany.count() > 0) {
            sb.appendln("\t\\\\ One to Many")

            oneToMany.joinTo(sb, "\n") { "\t" + it.classString() }
            sb.append("\n")
        }


        sb.append("\n\n")

        sb.appendln("\t\\\\ Helper Methods\n")

        // toString(): overridden if there is a name column
        if (dataColumns.count { it.name.contains("name", true) } > 0) {
            sb.append("\toverride fun toString(): String{\n\t\treturn this.${dataColumns.filter { it.name.contains("name", true) }.first().name}\n\t}")
        }


        sb.append("\n}\n")

        return sb.toString()
    }

    fun makeKotlin(): String = (objectString() + "\n\n" + classString())

    companion object {

        private fun deDBCase(dbCase: String): String {
            if (dbCase.startsWith('"')) {
                return dbCase.trim('"')
            } else
                return dbCase.toLowerCase()
        }

        fun makeFromDef(def: String): DaoTable {
            val tableName = deDBCase(Regex("create table [a-z]*.([\"a-zA-Z]*)", RegexOption.IGNORE_CASE).find(def)?.groupValues?.get(1)?.trim()
                    ?: "")

            val objectName = tableName.pluralize()
            val className = tableName.singularize()


            val columns = def.substringAfter('(').substringBeforeLast(')').split(",\n").map { DaoColumn.makeFromDef(it, objectName, className) }

            return DaoTable(objectName, className, columns)

        }
    }

}

abstract sealed class DaoColumn(val name: String, val objectName: String, val className: String = objectName.singularize()) {

    abstract fun displayString(): String
    abstract fun objectString(): String
    abstract fun classString(): String

    override fun toString(): String {
        return displayString()
    }

    companion object {
        private fun deDBCase(dbCase: String): String {
            if (dbCase.startsWith('"')) {
                return dbCase.trim('"')
            } else
                return dbCase.toLowerCase()
        }

        fun makeFromDef(definition: String, objectName: String, className: String = objectName.singularize()): DaoColumn {
            // I'm dealing with a key

            var def = definition.trim()

            if (def.startsWith("constraint", true)) {
                if (def.contains("primary key", true)) {
                    val typeString = Regex("[(][\" ,a-zA-Z]*[)]").find(def)?.value?.trim('(', ')') ?: ""

                    // single key
                    if (!typeString.contains(",")) {

                        return PrimaryKey(listOf(deDBCase(typeString)), objectName, className)
                    }

                    // composite key
                    else {
                        val strings = typeString.split(",").map { it.replace("\"\"", "\"").trim() }.map {
                            deDBCase(it)
                        }

                        return PrimaryKey(strings, objectName, className)
                    }
                } else if (def.contains("FOREIGN KEY", true)) {

                    val refObject = Regex("[(][\"a-zA-Z]*[)]").find(def.substringBefore("REFERENCES"))?.value?.trim('(', ')')
                            ?: ""

                    // set name
                    val name = deDBCase(refObject)


                    val refColumn = def.substringAfter("REFERENCES")

                    // set type
                    val typeMatch = Regex("[a-zA-Z ].([a-zA-Z\"]*) [(]([a-zA-Z\"]*)[)]").find(refColumn)?.groupValues
                    val type = deDBCase(typeMatch?.get(1) ?: "")

                    return ForigenKey(name.replace("id", "", true), type.singularize(), type.pluralize(), name,
                            false, objectName, className)

                } else {
                    return PrimaryKey(listOf(), objectName, className)
                }
            }
            // not a key
            else {


                // doesn't track not nulls
                if (def.endsWith("not null", true)) {
                    def = def.substringBeforeLast("NOT NULL").trim()
                    //NotNull = true
                }


                // set column name
                val name = deDBCase(def.substringBefore(' '))

                val typeString = def.substringAfter(" ")

                var args: List<Any> = emptyList()

                // set args if the type as size data
                if (typeString.contains('(')) {
                    val ns = Regex("[(][0-9,]*[)]").find(typeString)?.value?.trim('(', ')')?.split(",") ?: emptyList()

                    if (ns.size == 1)
                        args = listOf(ns[0].toInt())
                    else {
                        args = ns.map { it.toInt() }
                    }

                }

                // set type
                var type = typeString.substringBefore('(').trim()


                if (type == "character varying")
                    type = "varchar"

                if (type == "double precision") {
                    type = "decimal"
                    args = listOf(200, 200)
                }

                if (type.startsWith("text"))
                    type = "text"

                if (type == "numeric")
                    type = "decimal"

                if (type == "boolean")
                    type = "bool"

                if (args.count() < 1)
                    return DataColumn(name, type, false, objectName, className)
                else
                    return DataColumnWithArgs(name, type, false, objectName, className, args)

            }
        }
    }

}

open class DataColumn(name: String, val type: String, var primaryKey: Boolean = false, objectName: String, className: String = objectName.singularize()) : DaoColumn(name, objectName, className) {
    override fun classString() = "var $name by $objectName.$name"

    override fun objectString() = "val $name = $type($name)${if (primaryKey) ".primaryKey()" else ""}"

    override fun displayString() = "$name : $type"

}

class DataColumnWithArgs(name: String, type: String, primaryKey: Boolean = false, objectName: String, className: String = objectName.singularize(), val args: List<Any>) : DataColumn(name, type, primaryKey, objectName, className) {
    override fun classString() = "var $name by $objectName.$name"

    override fun objectString() = "var $name = $type($name, ${args.joinToString(", ")})${if (primaryKey) ".primaryKey()" else ""}"

    override fun displayString() = "$name : $type(${args.joinToString(", ")})"
}

class PrimaryKey(val fields: List<String>, objectName: String, className: String = objectName.singularize()) : DaoColumn("primary key", objectName, className) {
    override fun displayString(): String {
        return "DON'T USE.  INTERMEDIARY USED TO ASSIGN PRIMARY KEYS"
    }

    override fun objectString(): String {
        return "DON'T USE.  INTERMEDIARY USED TO ASSIGN PRIMARY KEYS"
    }

    override fun classString(): String {
        return "DON'T USE.  INTERMEDIARY USED TO ASSIGN PRIMARY KEYS"
    }

}

class ForigenKey(name: String, val referenceClass: String, val referenceObject: String, val referenceColumn: String, val many: Boolean, objectName: String, className: String = objectName.singularize()) : DaoColumn(name, objectName, className) {
    override fun classString(): String {
        if (!many) {
            return "val $name by $referenceObject referencedOn $objectName.$name"
        } else {
            return "val $name by $referenceClass referrersOn $referenceObject.$referenceColumn"
        }
    }

    override fun objectString(): String {
        if (!many) {
            return "val $name = reference($referenceObject, $referenceObject)"
        } else {
            return ""
        }
    }

    override fun displayString() = "$name : ${if (many) "List<$referenceClass>" else referenceClass} references $referenceObject on $referenceColumn"
}
