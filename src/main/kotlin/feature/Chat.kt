package chattore.feature

import chattore.ChattORE
import chattore.Feature
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.PlayerChatEvent
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class ChatConfig(
    val discord: String = "<dark_aqua>Discord</dark_aqua> <gray>|</gray> <dark_purple><sender></dark_purple><gray>:</gray> <message>"
)

fun createChatFeature(
    plugin: ChattORE,
    config: ChatConfig,
): Feature {
    return Feature(
        listeners = listOf(ChatListener(plugin, config))
    )
}

class ChatListener(
    val plugin: ChattORE,
    val config: ChatConfig,
) {

    private val flaggedMessages = ConcurrentHashMap<UUID, FlaggedMessageEvent>()

    @Subscribe(priority = 32767)
    fun onFlaggedMessageEvent(event: FlaggedMessageEvent) {
        flaggedMessages[event.sender.uniqueId] = event
    }

    @Subscribe
    fun onChatEvent(event: PlayerChatEvent) {
        flaggedMessages[event.player.uniqueId]?.let {
            if (it.message == event.message) {
                // message was flagged, do not send
                return
            } else {
                // message was not flagged, so remove if previous flagged message
                flaggedMessages.remove(event.player.uniqueId)
            }
        }
        val player = event.player
        val message = event.message
        player.currentServer.ifPresent { server ->
            plugin.logger.info("${player.username} (${player.uniqueId}): $message")
            plugin.messenger.broadcastChatMessage(server.serverInfo.name, player, message)
        }
    }
}
