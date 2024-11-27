package chattore.commands

import chattore.ChattORE
import chattore.render
import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Syntax
import com.velocitypowered.api.proxy.Player
import chattore.entity.ChattORESpec
import chattore.toComponent
import com.uchuhimo.konf.Config
import net.kyori.adventure.text.Component

@CommandAlias("lag")
@CommandPermission("chattore.lag")
class Lag(
    private val config: Config,
    private val chattORE: ChattORE
) : BaseCommand() {

    @Default
    @Syntax("")
    fun default(player: Player) {
        chattORE.logger.info("${player.username} lagg'd")
        chattORE.broadcast(
            config[ChattORESpec.format.lag].render(
                mapOf("sender" to player.username.toComponent())
            )
        )

        player.disconnect(Component.text("Should be fixed now! 🧇"))
    }
}
