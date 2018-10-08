package com.rnett.daogen.app

import javafx.beans.property.Property
import javafx.geometry.Pos
import javafx.scene.layout.VBox
import tornadofx.*

abstract class EditableItem : View() {
    abstract val name: String
    abstract var displayName: String
    abstract var otherDisplayName: String

    val model = EditableItemModel()

    inner class EditableItemModel : ItemViewModel<EditableItem>(this@EditableItem) {
        val displayName = bind(EditableItem::displayName, true)
        val otherDisplayName = bind(EditableItem::otherDisplayName, true)
    }

    fun VBox.propTextBox(label: String, property: Property<String>) = hbox {
        vboxConstraints { marginBottom = 20.0 }
        alignment = Pos.CENTER
        label(label) {
            hboxConstraints { marginRight = 10.0 }
        }
        textfield {
            textProperty().bindBidirectional(property)
            textProperty().onChange {
                fireEvent(DBChangeEvent())
            }
        }
    }

    fun VBox.propCheckBox(label: String, property: Property<Boolean>) = hbox {
        vboxConstraints { marginBottom = 20.0 }
        alignment = Pos.CENTER
        label(label) {
            hboxConstraints { marginRight = 10.0 }
        }
        checkbox {
            selectedProperty().bindBidirectional(property)
            selectedProperty().onChange {
                fireEvent(DBChangeEvent())
            }
        }
    }
}

interface HasItem {
    val item: EditableItem
}