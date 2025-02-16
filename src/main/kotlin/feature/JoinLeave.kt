package chattore.feature

import chattore.ChattORE
import chattore.Feature
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.player.ServerPostConnectEvent

data class JoinLeaveConfig(
    val join: String = "<yellow><player> has joined the network",
    val leave: String = "<yellow><player> has left the network",
    val joinDiscord: String = "**%player% has joined the network**",
    val leaveDiscord: String = "**%player% has left the network**",
)

fun createJoinLeaveFeature(
    plugin: ChattORE,
    config: JoinLeaveConfig
): Feature {
    return Feature(
        listeners = listOf(JoinLeaveListener(plugin, config))
    )
}

class JoinLeaveListener(
    val plugin: ChattORE,
    val config: JoinLeaveConfig
) {

    @Subscribe
    fun joinEvent(event: ServerPostConnectEvent) {
        if (event.previousServer != null) return
        val username = event.player.username
        plugin.messenger.broadcast(
            config.join.render(mapOf(
                "player" to username.toComponent()
            ))
        )
        val discordConnectEvent = DiscordBroadcastEventMain(
            config.joinDiscord,
            username,
        )
        plugin.proxy.eventManager.fire(discordConnectEvent).thenAccept { /* shrug */ }
    }

    @Subscribe
    fun leaveMessage(event: DisconnectEvent) {
        if (event.loginStatus != DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN) return
        val username = event.player.username
        plugin.messenger.broadcast(
            config.leave.render(mapOf(
                "player" to username.toComponent()
            ))
        )
        val discordDisconnectEvent = DiscordBroadcastEventMain(
            config.leaveDiscord,
            username,
        )
        plugin.proxy.eventManager.fire(discordDisconnectEvent).thenAccept { /* shrug */ }
    }
}
