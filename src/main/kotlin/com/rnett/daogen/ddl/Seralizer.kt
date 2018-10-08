package com.rnett.daogen.ddl

interface Seralizer<T, PARENT_TYPE> {
    fun create(parent: PARENT_TYPE): T
}

interface Seraliziable<T, P> {
    val data: Seralizer<T, P>
}