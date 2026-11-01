import androidx.compose.ui.Modifier
import com.kanvas.fx.core.Engine
import com.kanvas.fx.game.GameSceneController

fun consume(engine: Engine, controller: GameSceneController, modifier: Modifier) =
    Triple(engine, controller, modifier)
