import com.cesarferreira.pluralize.singularize
import com.sun.deploy.util.OrderedHashSet
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.print.attribute.standard.NumberUp

fun main(args: Array<String>) {
    var text: String

    if(args.size > 0)
        text = File(args[0]).readText()
    else
        text = System.`in`.bufferedReader().readText()

    text = text.replace("\r\n", "\n")

    val db = Database(text.split(";").filter { it.trim().startsWith("create table", true) }
            .map{Table(it)}.toMutableList())

    var out = """import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.dao.EntityID


"""

    out += db.makeKotlin()

    val sel = StringSelection(out)
    Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)

    println(out)

}

class Database(val tables: MutableList<Table> = ArrayList<Table>()){

    fun makeKotlin(): String{
        val forigenKeys = tables.flatMap { it.columns.filter { it.ForiegnKey } }
        tables.forEach {
            val tableName = it.tableName
            it.refrencingKeys.addAll(forigenKeys.filter { it.Type == tableName })
        }

        return tables.joinToString ("\n\n\n"){ it.makeKotlin() }

    }

}

class Table(val createStatement: String){

    val tableName: String
    val columns: MutableList<Column> = ArrayList<Column>()
    val primaryKeys: MutableSet<String> = LinkedHashSet<String>()
    val refrencingKeys: MutableList<Column> = ArrayList<Column>()

    init{

        val t = Regex("create table [a-z]*.([\"a-zA-Z]*)", RegexOption.IGNORE_CASE).find(createStatement)?.groupValues?.get(1)?.trim() ?: ""

        if(t.startsWith('"')){
            tableName = t.trim('"')
        } else
            tableName = t.toLowerCase()

        createStatement.substringAfter('(').substringBeforeLast(')').split(",\n").map { Column(it.trim(), this) }.forEach{columns.add(it)}

    }

    override fun toString(): String {
        return createStatement
    }


    fun makeKotlin(): String {

        val keyStr: String
        if(primaryKeys.size <= 1)
            keyStr = primaryKeys.first()
        else{
            val list = primaryKeys.toList()
            val sb1 = StringBuilder(list.first())

            for(i in 1..list.size-1){
                sb1.append("\\\" << ${8*i} | \\\"${list[i]}")
            }

            keyStr = sb1.toString()
        }

        val sb = StringBuilder( "object $tableName: IntIdTable(columnName = \"$keyStr\") {\n")
        columns.filter { !it.PrimaryKey && !it.ForiegnKey }.joinTo(sb, "\n"){
            var s = "\tval ${it.Name} = ${it.Type}(\"${it.Name}\""

            if(it.Number > 0)
                s += ", ${it.Number}"

            if(it.Numbers.isNotEmpty()){
                s += ", " + it.Numbers.joinToString(", ")
            }

            s += ")"

            if(this.primaryKeys.contains(it.Name))
                s += ".primaryKey()"

            s
        }

        sb.append("\n")

        columns.filter{it.ForiegnKey}.joinTo(sb, "\n"){
            "\tval ${it.Name.replace("id", "", true)} = reference(\"${it.Name}\", ${it.Type})"
        }


        if(primaryKeys.size > 1) {
            sb.append("\n\n\tfun idFromPKs(${primaryKeys.joinToString(", ") { it + ": Int" }}): Int {\n")
            val list = primaryKeys.toList()
            val sb1 = StringBuilder(list.first())

            for(i in 1..list.size-1){
                sb1.append(" shl ${8*i} or ${list[i]}")
            }

            sb.append("\t\treturn ${sb1.toString()}\n\t}\n")
        }

        if(primaryKeys.size <= 1){
            sb.append("\n\tfun findFromPKs(${primaryKeys.joinToString(", ") { it + ": Int" }}): ${tableName.singularize()}? {\n")
            sb.append("\t\treturn ${tableName.singularize()}.findById(${primaryKeys.joinToString(", ")})\n\t}\n")

        } else {
            sb.append("\n\tfun findFromPKs(${primaryKeys.joinToString(", ") { it + ": Int" }}): ${tableName.singularize()}? {\n")
            sb.append("\t\treturn ${tableName.singularize()}.findById(idFromPKs(${primaryKeys.joinToString(", ")}))\n\t}\n")
        }


        sb.append("\n}\n\n")

        sb.append("class ${tableName.singularize()}(id: EntityID<Int>): IntEntity(id) {\n")

        sb.append("\tcompanion object: IntEntityClass<${tableName.singularize()}>($tableName)\n\n")

        columns.filter { !it.PrimaryKey && !it.ForiegnKey }.joinTo(sb, "\n"){
            var s = "\tvar ${it.Name} by $tableName.${it.Name}"

            s
        }
        sb.append("\n")
        columns.filter{it.ForiegnKey}.joinTo(sb, "\n"){
            "\tval ${it.Name.replace("id", "", true)} by ${it.Type.singularize()} referencedOn $tableName.${it.Name.replace("id", "", true)}"
        }
        sb.append("\n")

        refrencingKeys.joinTo(sb, "\n"){
            "\tval ${this.tableName.singularize()}_${it.table.tableName} by ${it.table.tableName.singularize()} referrersOn ${it.table.tableName}.${it.Name.replace("id", "", true)}"
        }

        if(columns.count { it.Name.contains("name", true) } > 0){
            sb.append("\n\n\toverride fun toString(): String{\n\t\treturn this.${columns.filter { it.Name.contains("name", true) }.first().Name }\n\t}")
        }


        sb.append("\n}")


        sb.append("\n\n")

        return sb.toString();
    }

}

class Column(val statement: String, val table: Table){

    var NotNull: Boolean = false
    var Name: String = ""
    var Type: String = ""
    var Number:Int = -1
    var Numbers:List<Int> = emptyList()
    var ForiegnKey = false
    var PrimaryKey = false

    init{
        if(statement.startsWith("constraint", true)){

            if(statement.contains("primary key", true)){
                PrimaryKey = true
                Name = "Primary Key"
                val t = Regex("[(][\" ,a-zA-Z]*[)]").find(statement)?.value?.trim('(', ')') ?: ""

                if(!t.contains(",")) {

                    if (t.startsWith('"')) {
                        Type = t.trim('"')
                    } else
                        Type = t.toLowerCase()

                    table.primaryKeys.add(Type)
                } else {
                    val strings = t.split(",").map{it.replace("\"\"", "\"").trim()}.map{
                        if (it.startsWith('"'))
                            it.trim('"')
                        else
                            it.toLowerCase()
                    }

                    table.primaryKeys.addAll(strings)
                }
            } else{
                ForiegnKey = true;
                val t = Regex("[(][\"a-zA-Z]*[)]").find(statement.substringBefore("REFERENCES"))?.value?.trim('(', ')') ?: ""
                if(t.startsWith('"')){
                    Name = t.trim('"')
                } else
                    Name = t.toLowerCase()


                val ref = statement.substringAfter("REFERENCES")

                val m = Regex("[a-zA-Z ].([a-zA-Z\"]*) [(]([a-zA-Z\"]*)[)]").find(ref)?.groupValues

                Type =  m?.get(1)?.trim('"') ?: ""

            }

        } else {
            var t = statement;
            if(statement.endsWith("not null", true)){
                t = statement.substringBeforeLast("NOT NULL").trim()
                NotNull = true
            }

            val n = t.substringBefore(' ')
            if(n.startsWith('"')){
                Name = n.trim('"')
            } else
                Name = n.toLowerCase()

            val ty = t.substringAfter(" ")
            if(ty.contains('(')){
                val ns = Regex("[(][0-9,]*[)]").find(ty)?.value?.trim('(', ')')?.split(",") ?: emptyList()

                if(ns.size == 1)
                    Number = ns[0].toInt()
                else{
                    Numbers = ns.map{it.toInt()}
                }

            }

            Type = ty.substringBefore('(').trim()

            if(Type == "character varying")
                Type = "varchar"

            if(Type == "double precision") {
                Type = "decimal"
                Numbers = listOf(200, 200)
            }

            if(Type.startsWith("text"))
                Type = "text"

            if(Type == "numeric")
                Type = "decimal"

            if(Type == "boolean")
                Type = "bool"


        }



    }

    override fun toString(): String {
        val sb = StringBuilder("$Name $Type ")
        if(Number > 0)
            sb.append("(${Number}) ")

        if(NotNull)
            sb.append("NOT NULL")

        return sb.toString()
    }
}