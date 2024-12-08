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

@CommandAlias("c")
@CommandPermission("chattore.c")
class c(
    private val config: Config,
    private val chattORE: ChattORE
) : BaseCommand() {

    @Default
    @Syntax("")
    fun default(player: Player) {
        // Log the command execution
        chattORE.logger.info("${player.username} ran /c")

        // Fetch the message from the config and render placeholders if any
        val message = config[ChattORESpec.format.c].render(
            mapOf("player" to player.username.toComponent())
        )
        player.sendMessage(message)
    }
}
