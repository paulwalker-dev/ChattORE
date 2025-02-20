package chattore.feature

import chattore.Feature
import chattore.Messenger
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.PlayerChatEvent
import org.slf4j.Logger
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class ChatConfig(
    val discord: String = "<dark_aqua>Discord</dark_aqua> <gray>|</gray> <dark_purple><sender></dark_purple><gray>:</gray> <message>"
)

fun createChatFeature(
    logger: Logger,
    messenger: Messenger,
    config: ChatConfig,
): Feature {
    return Feature(
        listeners = listOf(ChatListener(logger, messenger, config))
    )
}

class ChatListener(
    private val logger: Logger,
    private val messenger: Messenger,
    private val config: ChatConfig,
) {
    @Subscribe
    fun onChatEvent(event: PlayerChatEvent) {
        if (!event.result.isAllowed) return
        val player = event.player
        val message = event.message
        player.currentServer.ifPresent { server ->
            logger.info("${player.username} (${player.uniqueId}): $message")
            messenger.broadcastChatMessage(server.serverInfo.name, player, message)
        }
    }
}
