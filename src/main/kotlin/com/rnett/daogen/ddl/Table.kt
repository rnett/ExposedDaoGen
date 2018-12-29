package com.rnett.daogen.ddl

import com.cesarferreira.pluralize.pluralize
import com.cesarferreira.pluralize.singularize
import com.rnett.core.advancedBuildString
import com.rnett.core.advancedBuildStringNoContract
import com.rnett.daogen.app.EditableItem
import com.rnett.daogen.database.DB
import javafx.scene.Parent
import tornadofx.*
import java.sql.JDBCType
import java.util.Collections.emptySet
import kotlin.contracts.ExperimentalContracts

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

private fun getPkIdString(pks: List<PrimaryKey>): String {

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
        rKs: Set<ForigenKey> = emptySet(),
        val database: Database
) : Seraliziable<Table, Database> {

    constructor(
            name: String,
            columns: Set<Column>,
            primaryKeys: Set<PrimaryKey>,
            database: Database,
            foreignKeys: Set<ForigenKey> = emptySet(),
            referencingKeys: Set<ForigenKey> = emptySet()
    ) : this(name, columns.groupBy { it.name }.mapValues { it.value.first() }, primaryKeys, foreignKeys, referencingKeys, database)

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
    enum class PKType(val valid: Boolean = true) {
        Int, Long, Composite, Other(false)
    }

    val primaryKey get() = primaryKeys.first()

    val pkType
        get() =
            when {
                primaryKeys.size > 1 && primaryKeys.all { it.key.type.type == Type.IntType || it.key.type.type == Type.LongType } -> PKType.Composite
                primaryKeys.size == 0 -> PKType.Other
                primaryKey.key.type.type == Type.IntType -> PKType.Int
                primaryKey.key.type.type == Type.LongType -> PKType.Long
                else -> PKType.Other
            }

    val objectName = name.toObjectName()
    val className = name.toClassName()

    var objectDisplayName = objectName
    var classDisplayName = className

    var makeConstructor = false

    override val data get() = Data(this)

    class Data(val name: String, val columns: List<Column>, val pks: List<Pair<String, Int>>, val className: String, val objectName: String, val makeConstructor: Boolean) : Seralizer<Table, Database> {
        constructor(table: Table) : this(table.name, table.columns.values.toList(), table.primaryKeys.map { Pair(it.key.name, it.index) }, table.classDisplayName, table.objectDisplayName, table.makeConstructor)

        override fun create(parent: Database): Table {
            val t = Table(name, columns.toSet(), pks.map { pair -> PrimaryKey(pair.second, columns.find { it.name == pair.first }!!) }.toSet(), parent)
            t.classDisplayName = className
            t.objectDisplayName = objectName

            return t
        }

    }

    inner class Display internal constructor(val isObject: Boolean) : EditableItem() {
        override var displayName
            get() = objectDisplayName
            set(v) {
                objectDisplayName = v
            }

        override var otherDisplayName
            get() = classDisplayName
            set(v) {
                classDisplayName = v
            }

        var makeConstructor
            get() = this@Table.makeConstructor
            set(v) {
                this@Table.makeConstructor = v
            }

        override val name: String = (if (isObject) "Object: " else "Class: ") + this@Table.toString()

        inner class MakeConstructorModel : ItemViewModel<Display>(this@Display) {
            val makeConstructor = bind(Display::makeConstructor, true)
        }

        val makeConstructorModel = MakeConstructorModel()

        override val root: Parent = vbox {
            paddingTop = 20
            propTextBox("Object Name: ", model.displayName)
            propTextBox("Class Name: ", model.otherDisplayName)
            propCheckBox("Make Constructor: ", makeConstructorModel.makeConstructor)
        }
    }

    val blacklisted = mutableSetOf<TableElement>()

    @ExperimentalContracts
    fun toKotlin(options: GenerationOptions) =
            "${makeForObject(options)}${if (options.doDao) "\n\n\n" + makeForClass(options) else ""}"

    val badClassNames get() = (database.tables.flatMap { listOf(it.classDisplayName, it.objectDisplayName) } + columns.filter { it.value !in blacklisted }.map { it.value.classDisplayName }).toSet()
    val badObjectNames get() = (database.tables.flatMap { listOf(it.classDisplayName, it.objectDisplayName) } + columns.filter { it.value !in blacklisted }.map { it.value.objectDisplayName }).toSet()

    @ExperimentalContracts
    fun makeForObject(options: GenerationOptions): String =
            advancedBuildString {

                append("object $objectDisplayName : ")
                append(when (pkType) {
                    PKType.Int -> "IntIdTable(\"$name\", \"${primaryKey.key.name}\")"
                    PKType.Long -> "LongIdTable(\"$name\", \"${primaryKey.key.name}\")"
                    PKType.Composite -> "IntIdTable(\"$name\", \"${getPkString(primaryKeys)}\")"

                    else -> "Table(\"$name\")"
                })

                appendln(" {")
                appendln()

                appendln("\t// Database Columns\n")

                columns.values.filter { it !in blacklisted }.forEach {
                    append("\t")
                    append(it.makeForObject(options))

                    primaryKeys.find { pk -> pk.key == it }?.apply {
                        if (primaryKeys.count() > 1)
                            append(".primaryKey($index)")
                        else
                            append(".primaryKey()")
                    }
                    +""
                }

                if (foreignKeys.any { it !in blacklisted && it.toTable.canMakeClass && it.fromTable.canMakeClass }) {
                    appendln("\n")

                    appendln("\t// Foreign/Imported Keys (One to Many)\n")

                    foreignKeys.filter { it !in blacklisted && it.toTable.canMakeClass && it.fromTable.canMakeClass }.forEach {
                        append("\t")
                        appendln(it.makeForeignForObject(options))
                    }
                }

                if (referencingKeys.any { it !in blacklisted && it.toTable.canMakeClass && it.fromTable.canMakeClass }) {
                    appendln("\n")

                    appendln("\t// Referencing/Exported Keys (One to Many)\n")

                    appendln("\t// ${referencingKeys.count { it !in blacklisted && it.toTable.canMakeClass && it.fromTable.canMakeClass }} keys.  Not present in object")
                }

                +"}"
            }

    fun makeForClass(options: GenerationOptions): String =
            advancedBuildStringNoContract {
                //TODO use the advanced features

                if (pkType == PKType.Composite || pkType == PKType.Other)
                    return@advancedBuildStringNoContract

                val keyType = when (pkType) {
                    PKType.Int -> "Int"
                    PKType.Long -> "Long"
                    else -> "<ERROR>"
                }


                if(options.serialization)
                    appendln("@Serializable(with=$classDisplayName.Companion::class)")

                appendln("${if (options.multiplatform) "actual " else ""}class $classDisplayName(${if (options.serialization) "val myId" else "id"}: EntityID<$keyType>) : ${keyType}Entity(${if (options.serialization) "myId" else "id"}) {\n")

                if (options.serialization)
                    appendln("\t@Serializer($classDisplayName::class)")

                append("\t${if (options.multiplatform) "actual " else ""}companion object : ${keyType}EntityClass<$classDisplayName>($objectDisplayName)")

                if (options.serialization)
                    append(", KSerializer<$classDisplayName>")

                appendln("{")

                if (options.serialization) {

                    if (options.dataTransfer) {
                        +"\t\tactual fun getItem(id: $pkType) = transaction{ super.get(id) }"
                        +"\t\tactual fun allItems() = transaction{ super.all().toList() }"
                    }

                    if (!options.serializationIncludeColumns) {

                        append("\t\t")
                        appendln("${if (options.multiplatform) "actual " else ""}override val descriptor: SerialDescriptor = StringDescriptor.withName(\"$classDisplayName\")")
                        appendln()

                        append("\t\t")
                        appendln("${if (options.multiplatform) "actual " else ""}override fun serialize(output: Encoder, obj: $classDisplayName) {")
                        appendln("\t\t\toutput.encodeString(HexConverter.printHexBinary(obj.${primaryKey.key.name}.toString().toUtf8Bytes()))")
                        appendln("\t\t}\n")

                        append("\t\t")
                        appendln("${if (options.multiplatform) "actual " else ""}override fun deserialize(input: Decoder): $classDisplayName {")
                        appendln("\t\t\treturn $classDisplayName[stringFromUtf8Bytes(HexConverter.parseHexBinary(input.decodeString())).to$pkType()]")
                        appendln("\t\t}\n")
                    } else {

                        val indicies = columns.values.filter { it !in blacklisted }.toList().mapIndexed { i, c -> Pair(i, c) }.toMap()

                        val pkIndex = indicies.entries.find { it.value == primaryKey.key }!!.key

                        append("\t\t")
                        appendln("${if (options.multiplatform) "actual " else ""}override val descriptor: SerialDescriptor = object : SerialClassDescImpl(\"$classDisplayName\") {")
                        appendln("\t\t\tinit{")
                        indicies.entries.sortedBy { it.key }.forEach {
                            appendln("\t\t\t\taddElement(\"${it.value.name}\")")
                        }
                        appendln("\t\t\t}")
                        appendln("\t\t}\n")

                        append("\t\t")
                        appendln("${if (options.multiplatform) "actual " else ""}override fun serialize(output: Encoder, obj: $classDisplayName) {")
                        appendln("\t\t\tval compositeOutput: CompositeEncoder = output.beginStructure(descriptor)")
                        indicies.entries.sortedBy { it.key }.forEach {
                            appendln("\t\t\tcompositeOutput.encodeStringElement(descriptor, ${it.key}, HexConverter.printHexBinary(obj.${it.value.name}.toString().toUtf8Bytes()))")
                        }
                        appendln("\t\t\tcompositeOutput.endStructure(descriptor)")
                        appendln("\t\t}\n")

                        append("\t\t")
                        appendln("${if (options.multiplatform) "actual " else ""}override fun deserialize(input: Decoder): $classDisplayName {")
                        appendln("\t\t\tval inp: CompositeDecoder = input.beginStructure(descriptor)")
                        appendln("\t\t\tvar id: $pkType? = null")
                        appendln("\t\t\tloop@ while (true) {")

                        appendln("\t\t\t\twhen (val i = inp.decodeElementIndex(descriptor)) {")
                        appendln("\t\t\t\t\tCompositeDecoder.READ_DONE -> break@loop")
                        appendln("\t\t\t\t\t$pkIndex -> id = stringFromUtf8Bytes(HexConverter.parseHexBinary(inp.decodeStringElement(descriptor, i))).to$pkType()")
                        appendln("\t\t\t\t\telse -> if (i < descriptor.elementsCount) continue@loop else throw SerializationException(\"Unknown index \$i\")")
                        appendln("\t\t\t\t}")
                        appendln("\t\t\t}\n")
                        appendln("\t\t\tinp.endStructure(descriptor)")
                        appendln("\t\t\tif(id == null)")
                        appendln("\t\t\t\tthrow SerializationException(\"Id '${primaryKey.key.name}' @ index $pkIndex not found\")")
                        appendln("\t\t\telse")
                        appendln("\t\t\t\treturn $classDisplayName[id]")

                        appendln("\t\t}\n")

                    }

                    append("\t\t")
                    appendln("${if (options.multiplatform) "actual " else ""}fun serializer(): KSerializer<$classDisplayName> = this")
                    appendln()
                }

                if (makeConstructor) {
                    append("\t\t")
                    appendln("fun new(${columns.values.filter { it !in blacklisted }.joinToString(", ") {
                        "${it.name}: ${it.type.type.kotlinType}"
                    }}) = new {")
                    columns.values.filter { it !in blacklisted }.forEach {
                        appendln("\t\t\t_${it.name} = ${it.name}")
                    }
                    appendln("\t\t}")
                }

                appendln("\t}\n")

                appendln("\t// Database Columns\n")

                columns.values.filter { it !in blacklisted }.forEach {
                    append("\t")
                    appendln(it.makeForClass(objectDisplayName, makeConstructor, options))
                }

                if (foreignKeys.any { it !in blacklisted && it.toTable.canMakeClass && it.fromTable.canMakeClass }) {
                    appendln("\n")

                    appendln("\t// Foreign/Imported Keys (One to Many)\n")

                    foreignKeys.filter { it !in blacklisted && it.toTable.canMakeClass && it.fromTable.canMakeClass }.forEach {
                        append("\t")
                        appendln(it.makeForeignForClass(options))
                    }
                }

                if (referencingKeys.any { it !in blacklisted && it.toTable.canMakeClass && it.fromTable.canMakeClass }) {
                    appendln("\n")

                    appendln("\t// Referencing/Exported Keys (One to Many)\n")

                    referencingKeys.filter { it !in blacklisted && it.toTable.canMakeClass && it.fromTable.canMakeClass }.forEach {
                        append("\t")
                        appendln(it.makeReferencingForClass(options))
                    }
                }

                appendln("\n")

                appendln("\t// Helper Methods\n")

                appendln("\t${if (options.multiplatform) "actual " else ""}override fun equals(other: Any?): Boolean {")
                appendln("\t\tif(other == null || other !is $classDisplayName)")
                appendln("\t\t\treturn false")
                appendln()
                appendln("\t\treturn ${primaryKey.key.name} == other.${primaryKey.key.name}")
                appendln("\t}")

                appendln("\n")
                appendln("\t${if (options.multiplatform) "actual " else ""}override fun hashCode() = ${primaryKey.key.name}${if (pkType == PKType.Long) ".hashCode()" else ""} ")

                appendln("\n")

                if (columns.values.filter { it.isNameColumn }.size == 1)
                    appendln("\t${if (options.multiplatform) "actual " else ""}override fun toString() = ${columns.values.find { it.isNameColumn }!!.name}")

                appendln("}")

            }

    val canMakeClass get() = pkType != PKType.Composite && pkType != PKType.Other

    @ExperimentalContracts
    fun makeClassForCommon(options: GenerationOptions) = advancedBuildString {

        if (pkType == PKType.Composite || pkType == PKType.Other)
            return@advancedBuildString

        if(options.serialization)
            appendln("@Serializable(with=$classDisplayName.Companion::class)")

        +"expect class $classDisplayName"
        codeBlock {
            columns.values.filter { it !in blacklisted }.forEach {
                +it.makeForCommon()
            }
            //TODO figure out how I want to handle references.  (probably using kframe-data to make the get operator available)

            /*
            foreignKeys.filter { it !in blacklisted }.forEach {
                +it.makeForeignForCommon()
            }
            referencingKeys.filter { it !in blacklisted }.forEach {
                +it.makeReferencingForCommon()
            }*/
            +""
            +"override fun equals(other: Any?): Boolean"
            +"override fun hashCode(): Int"
            if (columns.values.filter { it.isNameColumn }.size == 1)
                +"override fun toString(): String"


            if (options.serialization) {
                +""
                +"@Serializer($classDisplayName::class)"
                +"companion object : KSerializer<$classDisplayName>"
                codeBlock {


                    if (options.dataTransfer) {
                        +"fun getItem(id: $pkType): $classDisplayName"
                        +"fun allItems(): List<$classDisplayName>"
                        //TODO ways to get forigen/referencing key objects
                    }

                    +"override val descriptor: SerialDescriptor\n"
                    +"override fun serialize(output: Encoder, obj: $classDisplayName)\n"
                    +"override fun deserialize(input: Decoder): $classDisplayName\n"
                    +"fun serializer(): KSerializer<$classDisplayName>"
                }
            }
        }
    }

    @ExperimentalContracts
    fun makeClassForJS(options: GenerationOptions) = advancedBuildString {

        if (pkType == PKType.Composite || pkType == PKType.Other)
            return@advancedBuildString

        if(options.serialization)
            appendln("@Serializable(with=$classDisplayName.Companion::class)")
        +"actual data class $classDisplayName("
        +"\t${columns.values.filter { it !in blacklisted }.joinToString(",\n\t") { it.makeForJS() }}"
        /*
        +"\t${foreignKeys.filter { it !in blacklisted }.joinToString(",\n\t") { it.makeForeignForJS() }}"
        +"\t${referencingKeys.filter { it !in blacklisted }.joinToString(",\n\t") { it.makeReferencingForJS() }}"
        */
        +"){"

        appendln("\tactual override fun equals(other: Any?): Boolean {")
        appendln("\t\tif(other == null || other !is $classDisplayName)")
        appendln("\t\t\treturn false")
        appendln()
        appendln("\t\treturn ${primaryKey.key.name} == other.${primaryKey.key.name}")
        appendln("\t}")

        appendln("\n")
        appendln("\tactual override fun hashCode() = ${primaryKey.key.name}${if (pkType == PKType.Long) ".hashCode()" else ""}")

        appendln("\n")

        if (columns.values.filter { it.isNameColumn }.size == 1)
            appendln("\tactual override fun toString() = ${columns.values.find { it.isNameColumn }!!.name}")

        +""

        if (options.serialization) {

            +"\t@Serializer($classDisplayName::class)"
            +"\tactual companion object : KSerializer<$classDisplayName> {"

            if (options.dataTransfer) {
                +"\t\tactual fun getItem(id: $pkType): $classDisplayName = callEndpoint(this::getItem, ${options.requestClientName}, id)"
                +"\t\tactual fun allItems(): List<$classDisplayName> = callEndpoint(this::allItems, ${options.requestClientName})"
            }

            if (!options.serializationIncludeColumns) {

                append("\t\t")
                appendln("actual override val descriptor: SerialDescriptor = StringDescriptor.withName(\"$classDisplayName\")")
                appendln()

                append("\t\t")
                appendln("actual override fun serialize(output: Encoder, obj: $classDisplayName) {")
                appendln("\t\t\toutput.encodeString(HexConverter.printHexBinary(obj.${primaryKey.key.name}.toString().toUtf8Bytes()))")
                appendln("\t\t}\n")

                append("\t\t")
                appendln("actual override fun deserialize(input: Decoder): $classDisplayName {")
                appendln("\t\t\treturn $classDisplayName[stringFromUtf8Bytes(HexConverter.parseHexBinary(input.decodeString())).toInt()]")
                appendln("\t\t}\n")
            } else {

                val indicies = columns.values.filter { it !in blacklisted }.toList().mapIndexed { i, c -> Pair(i, c) }.toMap()

                val pkIndex = indicies.entries.find { it.value == primaryKey.key }

                append("\t\t")
                appendln("actual override val descriptor: SerialDescriptor = object : SerialClassDescImpl(\"$classDisplayName\") {")
                appendln("\t\t\tinit{")
                indicies.entries.sortedBy { it.key }.forEach {
                    appendln("\t\t\t\taddElement(\"${it.value.name}\")")
                }
                appendln("\t\t\t}")
                appendln("\t\t}\n")

                append("\t\t")
                appendln("actual override fun serialize(output: Encoder, obj: $classDisplayName) {")
                appendln("\t\t\tval compositeOutput: CompositeEncoder = output.beginStructure(descriptor)")
                indicies.entries.sortedBy { it.key }.forEach {
                    appendln("\t\t\tcompositeOutput.encodeStringElement(descriptor, ${it.key}, HexConverter.printHexBinary(obj.${it.value.name}.toString().toUtf8Bytes()))")
                }
                appendln("\t\t\tcompositeOutput.endStructure(descriptor)")
                appendln("\t\t}\n")

                append("\t\t")
                appendln("actual override fun deserialize(input: Decoder): $classDisplayName {")
                appendln("\t\t\tval inp: CompositeDecoder = input.beginStructure(descriptor)")

                indicies.entries.sortedBy { it.key }.forEach {
                    +"\t\t\tvar temp_${it.value.name}: ${it.value.type.type.kotlinType}? = null"
                }

                appendln("\t\t\tloop@ while (true) {")

                appendln("\t\t\t\twhen (val i = inp.decodeElementIndex(descriptor)) {")
                appendln("\t\t\t\t\tCompositeDecoder.READ_DONE -> break@loop")

                indicies.entries.sortedBy { it.key }.forEach { (index, col) ->
                    +"\t\t\t\t\t$index -> temp_${col.name} = stringFromUtf8Bytes(HexConverter.parseHexBinary(inp.decodeStringElement(descriptor, i)))${col.type.type.fromString}"
                }

                //appendln("\t\t\t\t\t$pkIndex -> id = HexConverter.parseHexBinary(inp.decodeStringElement(descriptor, i)).toString().toInt()")

                appendln("\t\t\t\t\telse -> if (i < descriptor.elementsCount) continue@loop else throw SerializationException(\"Unknown index \$i\")")
                appendln("\t\t\t\t}")
                appendln("\t\t\t}\n")
                appendln("\t\t\tinp.endStructure(descriptor)")

                +""

                appendln("\t\t\t\treturn $classDisplayName(${indicies.entries.sortedBy { it.key }.map { it.value }.joinToString(",\n\t\t\t\t", "\n\t\t\t\t", "\n\t\t\t") {
                    "temp_${it.name} ?: throw SerializationException(\"Missing value for ${it.name}\")"
                }})")

                appendln("\t\t}\n")

            }

            append("\t\t")
            appendln("actual fun serializer(): KSerializer<$classDisplayName> = this")
            +"\t}"
            appendln()
        }

        +"}"
    }

    //TODO kframe-data imports
    fun makeEndpointAdd() = listOf(
            "EndpointManager.addEndpoint($classDisplayName.Companion::getItem, $classDisplayName, ${pkType}Serializer)",
            "EndpointManager.addEndpoint($classDisplayName.Companion::allItems, $classDisplayName.list)"
    )

    override fun toString(): String {
        return buildString {
            appendln(name + ":")

            appendln("\tPrimary Key: ${
            when (primaryKeys.size) {
                0 -> "None"
                1 -> primaryKey.key.name
                else -> primaryKeys.joinToString(", ") { it.key.name }
            }
            }")
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
        if (columns != other.columns) return false
        if (primaryKey != other.primaryKey) return false
        if (_foreignKeys != other._foreignKeys) return false
        if (_referencingKeys != other._referencingKeys) return false
        if (objectDisplayName != other.objectDisplayName) return false
        if (classDisplayName != other.classDisplayName) return false
        if (blacklisted != other.blacklisted) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + columns.hashCode()
        result = 31 * result + primaryKey.hashCode()
        result = 31 * result + _foreignKeys.hashCode()
        result = 31 * result + _referencingKeys.hashCode()
        result = 31 * result + objectDisplayName.hashCode()
        result = 31 * result + classDisplayName.hashCode()
        result = 31 * result + blacklisted.hashCode()
        return result
    }


    companion object {
        operator fun invoke(name: String, database: Database): Table {
            val cols = DB.connection!!.metaData.getColumns(null, database.schema, name, null).let {
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
                            typeName == "double" -> Type.DoubleType.withData()
                            typeName == "real" -> Type.FloatType.withData()
                            typeName == "float" -> Type.FloatType.withData()
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

            val pks = DB.connection!!.metaData.getPrimaryKeys(null, database.schema, name).let {
                generateSequence {
                    if (it.next()) {
                        Pair(it.getString(4), it.getInt(5))
                    } else null
                }.filterNotNull().toList()  // must be inside the use() block
            }.map { PrimaryKey(it.second, cols[it.first]!!) }

            /*

            */

            return Table(name, cols.values.toSet(), pks.toSet(), database)
        }
    }

    val classDisplay get() = Display(false)
    val objectDisplay get() = Display(true)
}