package org.openredstone.chattore.feature

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.command.CommandExecuteEvent
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.audience.Audience
import org.openredstone.chattore.*

private object SpyEnabled : Setting<Boolean>("spy")

data class SpyingConfig(
    val spying: String = "<gold><sender>: <message>",
)

fun PluginScope.createSpyingFeature(
    database: Storage,
    config: SpyingConfig,
) {
    registerCommands(CommandSpy(database))
    registerListeners(CommandListener(database, proxy, config))
}

private class CommandListener(
    private val database: Storage,
    proxy: ProxyServer,
    private val config: SpyingConfig,
) {

    private val Player.isSpying: Boolean get() = database.getSetting(SpyEnabled, uniqueId) ?: false
    private val spies: Audience = proxy.all { it.hasChattorePrivilege && it.isSpying }

    @Subscribe
    fun onCommandEvent(event: CommandExecuteEvent) {
        spies.sendMessage(
            config.spying.render(
                "message" toS event.command,
                "sender" toS ((event.commandSource as? Player)?.username ?: "Console"),
            )
        )
    }
}

@CommandAlias("commandspy")
@CommandPermission("chattore.commandspy")
private class CommandSpy(
    private val database: Storage,
) : BaseCommand() {

    @Default
    fun default(player: Player) {
        val setting = database.getSetting(SpyEnabled, player.uniqueId)
        val newSetting = !(setting ?: false)
        database.setSetting(SpyEnabled, player.uniqueId, newSetting)
        player.sendInfo(
            if (newSetting) {
                "You are now spying on commands."
            } else {
                "You are no longer spying on commands."
            },
        )
    }
}
