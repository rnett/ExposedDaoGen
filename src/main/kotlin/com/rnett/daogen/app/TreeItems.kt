package com.rnett.daogen.app

import com.rnett.daogen.ddl.Table
import javafx.scene.control.TreeItem

class ClassItem(val table: Table) : TreeItem<String>(table.className)

class ObjectItem(val table: Table) : TreeItem<String>(table.objectName)