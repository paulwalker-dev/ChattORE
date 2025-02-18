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

data class FlaggedMessageEvent(
    val sender: Player,
    val message: String,
    val matchedRegexes: List<Regex>,
)

fun createChatConfirmationFeature(
    plugin: ChattORE,
    config: ChatConfirmationConfig,
): Feature {
    val flaggedMessages = ConcurrentHashMap<UUID, FlaggedMessageEvent>()
    return Feature(
        commands = listOf(ConfirmMessage(config, flaggedMessages, plugin)),
        listeners = listOf(ChatConfirmationListener(config, flaggedMessages, plugin.logger, plugin.proxy.eventManager)),
    )
}

class ChatConfirmationListener(
    private val config: ChatConfirmationConfig,
    private val flaggedMessages: ConcurrentHashMap<UUID, FlaggedMessageEvent>,
    private val logger: Logger,
    private val eventManager: EventManager,
) {

    private val regexes = config.regexes.map { Regex(it) }

    @Subscribe(priority = 32767)
    fun onChatEvent(event: PlayerChatEvent) {
        val player = event.player
        val message = event.message
        val matches = regexes.filter { it.containsMatchIn(message) }
        if (matches.isNotEmpty()) {
            var replaced = message
            matches.forEach {
                replaced = it.replace(replaced) { match ->
                    "<red>${match.value}</red>"
                }
            }
            logger.info("${player.username} (${player.uniqueId}) Attempting to send flagged message: $message")
            player.sendMessage(config.confirmationPrompt.render(
                mapOf("message" to replaced.miniMessageDeserialize())
            ))
            val flaggedMessageEvent = FlaggedMessageEvent(event.player, message, matches)
            eventManager.fire(flaggedMessageEvent).thenAccept { /* shrug */ }
            flaggedMessages[player.uniqueId] = flaggedMessageEvent
            return
        }
        flaggedMessages.remove(player.uniqueId)
    }
}

@CommandAlias("confirmmessage")
@CommandPermission("chattore.confirmmessage")
class ConfirmMessage(
    private val config: ChatConfirmationConfig,
    private val flaggedMessages: ConcurrentHashMap<UUID, FlaggedMessageEvent>,
    private val plugin: ChattORE,
) : BaseCommand() {
    @Default
    fun default(player: Player) {
        val message = flaggedMessages[player.uniqueId] ?:
            throw ChattoreException("You have no message to confirm!")
        plugin.logger.info("${player.username} (${player.uniqueId}) FLAGGED MESSAGE OVERRIDE: ${message.message}")
        player.sendMessage(config.chatConfirm.miniMessageDeserialize())
        player.currentServer.ifPresent { server ->
            plugin.messenger.broadcastChatMessage(server.serverInfo.name, player, message.message)
        }
        flaggedMessages.remove(player.uniqueId)
    }
}
