package org.openredstone.chattore.feature

import org.openredstone.chattore.*
import com.velocitypowered.api.event.EventManager
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.player.ServerPostConnectEvent
import com.velocitypowered.api.proxy.ProxyServer

data class JoinLeaveConfig(
    val join: String = "<yellow><player> has joined the network",
    val leave: String = "<yellow><player> has left the network",
    val joinDiscord: String = "**%player% has joined the network**",
    val leaveDiscord: String = "**%player% has left the network**",
)

fun createJoinLeaveFeature(
    proxy: ProxyServer,
    eventManager: EventManager,
    config: JoinLeaveConfig,
): Feature {
    return Feature(
        listeners = listOf(JoinLeaveListener(proxy, eventManager, config))
    )
}

class JoinLeaveListener(
    private val proxy: ProxyServer,
    private val eventManager: EventManager,
    private val config: JoinLeaveConfig,
) {

    @Subscribe
    fun joinEvent(event: ServerPostConnectEvent) {
        if (event.previousServer != null) return
        val username = event.player.username
        proxy.all.sendRichMessage(
            config.join,
            "player" toS username,
        )
        val discordConnectEvent = DiscordBroadcastEventMain(
            config.joinDiscord,
            username,
        )
        eventManager.fireAndForget(discordConnectEvent)
    }

    @Subscribe
    fun leaveMessage(event: DisconnectEvent) {
        if (event.loginStatus != DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN) return
        val username = event.player.username
        proxy.all.sendRichMessage(
            config.leave,
            "player" toS username,
        )
        val discordDisconnectEvent = DiscordBroadcastEventMain(
            config.leaveDiscord,
            username,
        )
        eventManager.fireAndForget(discordDisconnectEvent)
    }
}
