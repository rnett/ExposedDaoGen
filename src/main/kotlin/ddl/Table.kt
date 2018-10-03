package ddl

import database.DB
import java.sql.JDBCType

interface DBItem {
    fun makeForObject(): String
    fun makeForClass(): String
}

data class PrimaryKey(val index: Int, val key: Column) {
    override fun toString(): String = key.name
}

val Iterable<PrimaryKey>.columns get() = this.map { it.key }
val Sequence<PrimaryKey>.columns get() = this.map { it.key }

class Table(
        val name: String,
        val columns: Set<Column>,
        val primaryKeys: Set<PrimaryKey>,
        val forigenKeys: Set<ForigenKey>,
        val referencingKeys: Set<ForigenKey>
) : DBItem {
    override fun makeForObject(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun makeForClass(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun toString(): String {
        return buildString {
            appendln(name + ":")

            appendln("\tPrimary Key(s): ${primaryKeys.sortedBy { it.index }.joinToString(", ")}")
            appendln()

            columns.forEach {
                appendln("\t$it")
            }

            if (forigenKeys.isNotEmpty()) {
                appendln()
                appendln("\tForeign Key(s):")
                forigenKeys.forEach {
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

    companion object {
        operator fun invoke(name: String): Table {
            val cols = DB.connection!!.metaData.getColumns(null, null, name, null).use {
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
                            typeName == "double" -> Type.FloatType.withData()
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

            val pks = DB.connection!!.metaData.getPrimaryKeys(null, null, name).use {
                generateSequence {
                    if (it.next()) {
                        Pair(it.getString(4), it.getInt(5))
                    } else null
                }.filterNotNull().toList()  // must be inside the use() block
            }.map { PrimaryKey(it.second, cols[it.first]!!) }


            val fks = DB.connection!!.metaData.getImportedKeys(null, null, name).use {
                generateSequence {
                    if (it.next()) {

                        ForigenKey(it.getString(7), it.getString(3),
                                it.getString(8), it.getString(4))

                    } else null
                }.filterNotNull().toList()  // must be inside the use() block
            }


            val rks = DB.connection!!.metaData.getExportedKeys(null, null, name).use {
                generateSequence {
                    if (it.next()) {

                        ForigenKey(it.getString(7), it.getString(3),
                                it.getString(8), it.getString(4))

                    } else null
                }.filterNotNull().toList()  // must be inside the use() block
            }

            return Table(name, cols.values.toSet(), pks.toSet(), fks.toSet(), rks.toSet())
        }
    }

}