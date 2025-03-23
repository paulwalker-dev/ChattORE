package chattore.feature

import chattore.*
import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Syntax
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger

data class HelpOpConfig(
    val format: String = "<gold>[</gold><red>Help</red><gold>]</gold> <red><sender></red><gold>:</gold> <message>",
)

fun createHelpOpFeature(
    logger: Logger,
    proxy: ProxyServer,
    config: HelpOpConfig,
): Feature {
    return Feature(
        commands = listOf(HelpOp(logger, proxy, config))
    )
}

@CommandAlias("helpop|ac")
@CommandPermission("chattore.helpop")
class HelpOp(
    private val logger: Logger,
    private val proxy: ProxyServer,
    private val config: HelpOpConfig,
) : BaseCommand() {

    @Default
    @Syntax("[message]")
    fun default(player: Player, statement: String) {
        if (statement.isEmpty()) throw ChattoreException("You have to have a problem first!") // : )
        logger.info("[HelpOp] ${player.username}: $statement")
        val message = config.format.render(
            "message" toS statement,
            "sender" toS player.username,
        )
        proxy.all { it.hasChattorePrivilege || it.uniqueId == player.uniqueId }
            .sendMessage(message)
    }
}
