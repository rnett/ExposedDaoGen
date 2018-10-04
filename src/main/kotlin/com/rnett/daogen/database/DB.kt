package com.rnett.daogen.database

import java.sql.Connection
import java.sql.DriverManager

object DB {
    private var _connection: Connection? = null
    val connection get() = _connection

    fun connect(str: String): Connection {
        Class.forName("org.postgresql.Driver").newInstance()
        _connection = DriverManager.getConnection(str)
        return _connection!!
    }
}