package chattore.feature

import chattore.*
import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import com.velocitypowered.api.event.EventManager
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.PlayerChatEvent
import com.velocitypowered.api.proxy.Player
import org.slf4j.Logger
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class ChatConfirmationConfig(
    val regexes: List<String> = listOf(),
    val confirmationPrompt: String = "\"<red><bold>The following message was not sent because it contained " +
        "potentially inappropriate language:<newline><reset><message><newline><red>To send this message anyway, run " +
        "<gray>/confirmmessage<red>.\"",
    val chatConfirm: String = "<red>Override recognized"
)

fun createChatConfirmationFeature(
    logger: Logger,
    messenger: Messenger,
    config: ChatConfirmationConfig,
): Feature {
    val flaggedMessages = ConcurrentHashMap<UUID, String>()
    return Feature(
        commands = listOf(ConfirmMessage(config, flaggedMessages, logger, messenger)),
        listeners = listOf(ChatConfirmationListener(config, flaggedMessages, logger)),
    )
}

class ChatConfirmationListener(
    private val config: ChatConfirmationConfig,
    private val flaggedMessages: ConcurrentHashMap<UUID, String>,
    private val logger: Logger,
) {
    private val regexes = config.regexes.map(::Regex)

    @Subscribe(priority = Short.MAX_VALUE)
    fun onChatEvent(event: PlayerChatEvent) {
        val player = event.player
        val message = event.message
        val matches = regexes.filter { it.containsMatchIn(message) }
        if (matches.isEmpty()) {
            flaggedMessages.remove(player.uniqueId)
            return
        }
        event.result = PlayerChatEvent.ChatResult.denied()
        fun String.highlight(r: Regex) = r.replace(this) { match -> "<red>${match.value}</red>" }
        val highlighted = matches.fold(message, String::highlight)
        logger.info("${player.username} (${player.uniqueId}) Attempting to send flagged message: $message")
        player.sendSimpleMM(config.confirmationPrompt, highlighted)
        flaggedMessages[player.uniqueId] = event.message
    }
}

@CommandAlias("confirmmessage")
@CommandPermission("chattore.confirmmessage")
class ConfirmMessage(
    private val config: ChatConfirmationConfig,
    private val flaggedMessages: ConcurrentHashMap<UUID, String>,
    private val logger: Logger,
    private val messenger: Messenger,
) : BaseCommand() {
    @Default
    fun default(player: Player) {
        val message = flaggedMessages[player.uniqueId] ?:
            throw ChattoreException("You have no message to confirm!")
        logger.info("${player.username} (${player.uniqueId}) FLAGGED MESSAGE OVERRIDE: $message")
        player.sendRichMessage(config.chatConfirm)
        player.currentServer.ifPresent { server ->
            messenger.broadcastChatMessage(server.serverInfo.name, player, message)
        }
        flaggedMessages.remove(player.uniqueId)
    }
}
