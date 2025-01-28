package chattore.commands

import chattore.ChattORE
import chattore.ChattoreException
import chattore.render
import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Syntax
import com.uchuhimo.konf.Config
import com.velocitypowered.api.proxy.Player
import chattore.entity.ChattORESpec
import chattore.toComponent

@CommandAlias("me")
@CommandPermission("chattore.me")
class Me(
    private val config: Config,
    private val chattORE: ChattORE
) : BaseCommand() {

    @Default
    @Syntax("[message]")
    fun default(player: Player, args: Array<String>) {
        if (args.isEmpty()) throw ChattoreException("You have to have a thonk first!")
        val statement = args.joinToString(" ")
        chattORE.logger.info("* ${player.username} $statement")
        chattORE.broadcast(
            config[ChattORESpec.format.me].render(
                mapOf(
                    "message" to statement.toComponent(),
                    "sender" to player.username.toComponent()
                )
            )
        )
    }
}
