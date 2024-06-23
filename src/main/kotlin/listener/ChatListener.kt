package chattore.listener

import chattore.ChattORE
import chattore.discordEscape
import chattore.entity.ChattORESpec
import chattore.render
import chattore.toComponent
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.command.CommandExecuteEvent
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.player.PlayerChatEvent
import com.velocitypowered.api.event.player.ServerPostConnectEvent
import com.velocitypowered.api.event.player.ServerPreConnectEvent
import com.velocitypowered.api.event.player.TabCompleteEvent
import com.velocitypowered.api.proxy.Player
import java.util.concurrent.TimeUnit

class ChatListener(
    private val chattORE: ChattORE
) {
    @Subscribe
    fun onTabComplete(event: TabCompleteEvent) {
        // TODO: Autocomplete player names and stuff idk
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
        val nickname = chattORE.database.getNickname(event.player.uniqueId);
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
        pp.currentServer.ifPresent { server ->
            chattORE.logger.info("${pp.username} (${pp.uniqueId}): ${event.message}")
            chattORE.broadcastChatMessage(server.serverInfo.name, pp.uniqueId, event.message)
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