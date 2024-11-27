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

@CommandAlias("waffle")
@CommandPermission("chattore.waffle")
class Waffle(
    private val config: Config,
    private val chattORE: ChattORE
) : BaseCommand() {

    @Default
    @Syntax("")
    fun default(player: Player) {
        chattORE.logger.info("${player.username} waffl'd")
        chattORE.broadcast(
            config[ChattORESpec.format.waffle].render(
                mapOf("sender" to player.username.toComponent())
            )
        )
    }
}
