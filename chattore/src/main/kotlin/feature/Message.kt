package chattore.feature

import chattore.*
import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class MessageConfig(
    val messageReceived: String = "<gold>[</gold><red><sender></red> <gold>-></gold> <red>me</red><gold>]</gold> <message>",
    val messageSent: String = "<gold>[</gold><red>me</red> <gold>-></gold> <red><recipient></red><gold>]</gold> <message>",
)

fun createMessageFeature(
    proxy : ProxyServer,
    logger : Logger,
    messenger: Messenger,
    config: MessageConfig
): Feature {
    val replyMap = ConcurrentHashMap<UUID, UUID>()
    return Feature(
        commands = listOf(
            Message(proxy, logger, messenger, config, replyMap),
            Reply(proxy, logger, messenger, config, replyMap)
        ),
    )
}

@CommandAlias("m|pm|msg|message|vmsg|vmessage|whisper|tell")
@CommandPermission("chattore.message")
private class Message(
    private val proxy: ProxyServer,
    private val logger: Logger,
    private val messenger: Messenger,
    private val config: MessageConfig,
    private val replyMap: ConcurrentHashMap<UUID, UUID>
) : BaseCommand() {
    @Default
    @Syntax("[target] <message>")
    @CommandCompletion("@players")
    fun default(player: Player, target: String, args: Array<String>) {
        proxy.getPlayer(target).ifPresentOrElse({ targetPlayer ->
            sendMessage(logger, messenger, replyMap, config, player, targetPlayer, args.joinToString(" "))
        }, {
            throw ChattoreException("That user doesn't exist!")
        })
    }
}

@CommandAlias("r|reply")
@CommandPermission("chattore.message")
private class Reply(
    private val proxy: ProxyServer,
    private val logger: Logger,
    private val messenger: Messenger,
    private val config: MessageConfig,
    private val replyMap: ConcurrentHashMap<UUID, UUID>
) : BaseCommand() {
    @Default
    fun default(player: Player, args: Array<String>) {
        proxy.getPlayer(
            replyMap[player.uniqueId] ?: throw ChattoreException(
                "You have no one to reply to!"
            )
        ).ifPresentOrElse({ target ->
            sendMessage(logger, messenger, replyMap, config, player, target, args.joinToString(" "))
        }, {
            throw ChattoreException(
                "The person you are trying to reply to is no longer online!"
            )
        })
    }
}

fun sendMessage(
    logger: Logger,
    messenger: Messenger,
    replyMap: MutableMap<UUID, UUID>,
    config: MessageConfig,
    player: Player,
    targetPlayer: Player,
    message: String
) {
    logger.info("${player.username} (${player.uniqueId}) -> " +
        "${targetPlayer.username} (${targetPlayer.uniqueId}): $message")
    player.sendMessage(
        config.messageSent.render(
            mapOf(
                "message" to messenger.prepareChatMessage(message, player),
                "recipient" to targetPlayer.username.toComponent()
            )
        )
    )
    targetPlayer.sendMessage(
        config.messageReceived.render(
            mapOf(
                "message" to messenger.prepareChatMessage(message, player),
                "sender" to player.username.toComponent()
            )
        )
    )
    replyMap[targetPlayer.uniqueId] = player.uniqueId
    replyMap[player.uniqueId] = targetPlayer.uniqueId
}
