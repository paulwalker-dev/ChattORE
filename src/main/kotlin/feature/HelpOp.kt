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
    fun default(player: Player, args: Array<String>) {
        if (args.isEmpty()) throw ChattoreException("You have to have a problem first!") // : )
        val statement = args.joinToString(" ")
        logger.info("[HelpOp] ${player.username}: $statement")
        val message = config.format.render(
            mapOf(
                "message" to statement.toComponent(),
                "sender" to player.username.toComponent()
            )
        )
        player.sendMessage(message)
        messenger.sendPrivileged(
            message,
            exclude = player.uniqueId,
            ignorable = false
        )
    }
}
