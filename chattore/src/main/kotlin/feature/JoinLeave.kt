package org.openredstone.chattore.feature

import com.velocitypowered.api.event.EventManager
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.player.ServerPostConnectEvent
import com.velocitypowered.api.proxy.ProxyServer
import org.openredstone.chattore.*

// TODO: format config is "too big" for this, it has things that this doesn't require.
//  Look at this during/after bubble implementation since that may change things here too.
fun PluginScope.createJoinLeaveFeature(config: FormatConfig) {
    registerListeners(JoinLeaveListener(proxy, proxy.eventManager, config))
}

private class JoinLeaveListener(
    private val proxy: ProxyServer,
    private val eventManager: EventManager,
    private val config: FormatConfig,
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
