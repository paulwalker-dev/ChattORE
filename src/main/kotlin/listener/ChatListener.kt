package chattore.listener

import chattore.*
import chattore.entity.ChattORESpec
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.command.CommandExecuteEvent
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.player.PlayerChatEvent
import com.velocitypowered.api.event.player.ServerPreConnectEvent
import com.velocitypowered.api.event.player.TabCompleteEvent
import com.velocitypowered.api.proxy.Player
import java.util.concurrent.TimeUnit

class ChatListener(
    private val chattORE: ChattORE
) {
    @Subscribe
    fun onTabComplete(event: TabCompleteEvent) {
        event.suggestions.clear()
    }

    @Subscribe
    fun onJoin(event: ServerPreConnectEvent) {
        chattORE.database.ensureCachedUsername(
            event.player.uniqueId,
            event.player.username
        )
    }

    @Subscribe
    fun joinEvent(event: LoginEvent) {
        joinMessage(event)
        val unreadCount = chattORE.database.getMessages(event.player.uniqueId).filter { !it.read }.size
        if (unreadCount > 0)
            chattORE.proxy.scheduler.buildTask(chattORE, Runnable {
                event.player.sendMessage(chattORE.config[ChattORESpec.format.mailUnread].render(mapOf(
                    "count" to "$unreadCount".toComponent()
                )))
            })
                .delay(2L, TimeUnit.SECONDS)
                .schedule()
        if (!chattORE.config[ChattORESpec.clearNicknameOnChange]) return
        val existingName = chattORE.database.uuidToUsernameCache[event.player.uniqueId] ?: return
        if (existingName == event.player.username) return
        val nickname = chattORE.database.getNickname(event.player.uniqueId)
        if (nickname?.contains("<username>") ?: false) return
        chattORE.database.removeNickname(event.player.uniqueId)
    }

    fun joinMessage(event: LoginEvent) {
        val username = event.player.username
        chattORE.broadcast(
            chattORE.config[ChattORESpec.format.join].render(mapOf(
                "player" to username.toComponent()
            ))
        )
        chattORE.broadcastPlayerConnection(
            chattORE.config[ChattORESpec.format.joinDiscord].replace(
                "<player>",
                username.discordEscape()
            )
        )
    }

    @Subscribe
    fun leaveMessage(event: DisconnectEvent) {
        if (event.loginStatus != DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN) return
        val username = event.player.username
        chattORE.broadcast(
            chattORE.config[ChattORESpec.format.leave].render(mapOf(
                "player" to username.toComponent()
            ))
        )
        chattORE.broadcastPlayerConnection(
            chattORE.config[ChattORESpec.format.leaveDiscord].replace(
                "<player>",
                username.discordEscape()
            )
        )
    }

    @Subscribe
    fun onChatEvent(event: PlayerChatEvent) {
        val pp = event.player
        val message = event.message
        val matches = chattORE.chatConfirmRegexes.filter { it.containsMatchIn(message) }
        if (matches.isNotEmpty()) {
            chattORE.chatConfirmMap[pp.uniqueId] = message
            var replaced = message
            matches.forEach {
                replaced = it.replace(replaced) { match ->
                    "<red>${match.value}</red>"
                }
            }
            chattORE.logger.info("${pp.username} (${pp.uniqueId}) Attempting to send flagged message: $message")
            pp.sendMessage(chattORE.config[ChattORESpec.format.chatConfirmPrompt].render(
                mapOf("message" to replaced.miniMessageDeserialize())
            ))
            return
        }
        chattORE.chatConfirmMap.remove(pp.uniqueId)
        pp.currentServer.ifPresent { server ->
            chattORE.logger.info("${pp.username} (${pp.uniqueId}): $message")
            chattORE.broadcastChatMessage(server.serverInfo.name, pp.uniqueId, message)
        }
    }

    @Subscribe
    fun onCommandEvent(event: CommandExecuteEvent) {
        chattORE.sendPrivileged(
            chattORE.config[ChattORESpec.format.commandSpy].render(
                mapOf(
                    "message" to event.command.toComponent(),
                    "sender" to ((event.commandSource as? Player)?.username ?: "Console").toComponent()
                )
            )
        )
    }
}