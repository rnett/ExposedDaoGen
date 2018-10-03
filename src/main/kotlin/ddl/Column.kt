package ddl

class Column(
        val name: String,
        val type: DataType,
        val notNull: Boolean,
        val autoIncrement: Boolean
) : DBItem {
    override fun makeForObject(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun makeForClass(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun toString(): String = "$name $type${if (notNull) " not null" else ""}${if (autoIncrement) " auto increment" else ""}"

}

class ForigenKey(
        val fromTableName: String,
        val toTableName: String,

        val fromColumnName: String,
        val toColumnName: String
) {

    fun makeReferencingForObject(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun makeReferencingForClass(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun makeReferencedForObject(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun makeReferencedForClass(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun toString(): String = "$fromTableName.$fromColumnName refers to $toTableName.$toColumnName"
}