package org.openredstone.chattore.feature

import com.velocitypowered.api.event.EventManager
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.player.ServerPostConnectEvent
import com.velocitypowered.api.proxy.ProxyServer
import org.openredstone.chattore.PluginScope
import org.openredstone.chattore.all
import org.openredstone.chattore.sendRichMessage
import org.openredstone.chattore.toS

data class JoinLeaveConfig(
    val join: String = "<yellow><player> has joined the network",
    val leave: String = "<yellow><player> has left the network",
    val joinDiscord: String = "**%player% has joined the network**",
    val leaveDiscord: String = "**%player% has left the network**",
)

fun PluginScope.createJoinLeaveFeature(config: JoinLeaveConfig) {
    registerListeners(JoinLeaveListener(proxy, proxy.eventManager, config))
}

private class JoinLeaveListener(
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
