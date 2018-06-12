import javafx.scene.layout.BorderPane
import tornadofx.App
import tornadofx.View

class DaoGenApp : App(DaoGenView::class)

class DaoGenView : View() {
    override val root = BorderPane()

}