package com.rnett.daogen.app

import com.rnett.daogen.ddl.Column
import com.rnett.daogen.ddl.ForigenKey
import com.rnett.daogen.ddl.Table
import javafx.event.Event
import javafx.scene.control.TreeItem

class ClassItem(val table: Table) : TreeItem<String>(table.classDisplayName), HasItem {
    override val item = table.classDisplay

    init {
        children.addAll(
                ColumnsItem(table, false),
                FKsItem(table, false),
                RKsItem(table, false))
        isExpanded = true

        item.model.displayName.addListener { _ ->
            Event.fireEvent(this@ClassItem, TreeModificationEvent(TreeItem.valueChangedEvent<String>(), this@ClassItem))
        }
        item.model.otherDisplayName.addListener { _ ->
            Event.fireEvent(this@ClassItem, TreeModificationEvent(TreeItem.valueChangedEvent<String>(), this@ClassItem))
        }
    }

}

class ObjectItem(val table: Table) : TreeItem<String>(table.objectDisplayName), HasItem {
    override val item = table.objectDisplay

    init {
        children.addAll(
                ColumnsItem(table, true),
                FKsItem(table, true),
                RKsItem(table, true))
        isExpanded = true

        item.model.displayName.addListener { _ ->
            Event.fireEvent(this@ObjectItem, TreeModificationEvent(TreeItem.valueChangedEvent<String>(), this@ObjectItem))
        }
        item.model.otherDisplayName.addListener { _ ->
            Event.fireEvent(this@ObjectItem, TreeModificationEvent(TreeItem.valueChangedEvent<String>(), this@ObjectItem))
        }
    }
}

class ColumnsItem(val table: Table, val isObject: Boolean) : TreeItem<String>("Columns") {
    init {
        children.addAll(table.columns.values.map { ColumnItem(it, isObject) })
        isExpanded = true
    }
}

class FKsItem(val table: Table, val isObject: Boolean) : TreeItem<String>("Foreign Keys") {
    init {
        children.addAll(table.foreignKeys.map { FKItem(it, isObject) })
        isExpanded = true
    }
}

class RKsItem(val table: Table, val isObject: Boolean) : TreeItem<String>("Referencing Keys") {
    init {
        if (!isObject)
            children.addAll(table.referencingKeys.map { RKItem(it) })
        isExpanded = true
    }
}

class ColumnItem(val column: Column, val isObject: Boolean) : TreeItem<String>(if (isObject) column.objectDisplayName else column.classDisplayName), HasItem {
    override val item = column.Display(isObject) //I have no idea why using the fields NPEs

    init {
        item.model.displayName.addListener { _ ->
            Event.fireEvent(this@ColumnItem, TreeModificationEvent(TreeItem.valueChangedEvent<String>(), this@ColumnItem))
        }
        item.model.otherDisplayName.addListener { _ ->
            Event.fireEvent(this@ColumnItem, TreeModificationEvent(TreeItem.valueChangedEvent<String>(), this@ColumnItem))
        }
    }
}

class FKItem(val key: ForigenKey, val isObject: Boolean) : TreeItem<String>(if (isObject) key.fkObjectName else key.fkClassName), HasItem {
    override val item = if (isObject) key.fkObjectDisplay else key.fkClassDisplay

    init {
        item.model.displayName.addListener { _ ->
            Event.fireEvent(this@FKItem, TreeModificationEvent(TreeItem.valueChangedEvent<String>(), this@FKItem))
        }
        item.model.otherDisplayName.addListener { _ ->
            Event.fireEvent(this@FKItem, TreeModificationEvent(TreeItem.valueChangedEvent<String>(), this@FKItem))
        }
    }
}

class RKItem(val key: ForigenKey) : TreeItem<String>(key.rkClassName), HasItem {
    override val item = key.rkDisplay

    init {
        item.model.displayName.addListener { _ ->
            Event.fireEvent(this@RKItem, TreeModificationEvent(TreeItem.valueChangedEvent<String>(), this@RKItem))
        }
        item.model.otherDisplayName.addListener { _ ->
            Event.fireEvent(this@RKItem, TreeModificationEvent(TreeItem.valueChangedEvent<String>(), this@RKItem))
        }
    }
}
