package chattore.feature

import chattore.*
import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Syntax
import com.velocitypowered.api.proxy.Player
import org.slf4j.Logger

data class HelpOpConfig(
    val format: String = "<gold>[</gold><red>Help</red><gold>]</gold> <red><sender></red><gold>:</gold> <message>",
)

fun createHelpOpFeature(
    logger: Logger,
    messenger: Messenger,
    config: HelpOpConfig
): Feature {
    return Feature(
        commands = listOf(HelpOp(logger, messenger, config))
    )
}

@CommandAlias("helpop|ac")
@CommandPermission("chattore.helpop")
class HelpOp(
    private val logger: Logger,
    private val messenger: Messenger,
    private val config: HelpOpConfig
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
        player.sendMessage(message)
        messenger.sendPrivileged(
            message,
            exclude = player.uniqueId,
            ignorable = false
        )
    }
}
