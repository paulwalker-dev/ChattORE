package chattore.feature

import chattore.*
import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.command.CommandExecuteEvent
import com.velocitypowered.api.proxy.Player

object SpyEnabled : Setting<Boolean>("spy")

data class SpyingConfig(
    val format: String = "<gold>[</gold><red>ChattORE</red><gold>]</gold> <red><message></red>",
    val spying: String = "<gold><sender>: <message>",
)

fun createSpyingFeature(
    database: Storage,
    messenger: Messenger,
    config: SpyingConfig,
): Feature {
    return Feature(
        commands = listOf(CommandSpy(database, config)),
        listeners = listOf(CommandListener(messenger, config))
    )
}

class CommandListener(
    private val messenger: Messenger,
    private val config: SpyingConfig
) {
    @Subscribe
    fun onCommandEvent(event: CommandExecuteEvent) {
        messenger.sendPrivileged(
            config.spying.render(
                "message" toS event.command,
                "sender" toS ((event.commandSource as? Player)?.username ?: "Console")
            )
        )
    }
}

@CommandAlias("commandspy")
@CommandPermission("chattore.commandspy")
class CommandSpy(
    private val database: Storage,
    private val config: SpyingConfig,
) : BaseCommand() {

    @Default
    fun default(player: Player) {
        val setting = database.getSetting(SpyEnabled, player.uniqueId)
        val newSetting = !(setting ?: false)
        database.setSetting(SpyEnabled, player.uniqueId, newSetting)
        player.sendSimpleS(
            config.format,
            if (newSetting) {
                "You are now spying on commands."
            } else {
                "You are no longer spying on commands."
            },
        )
    }
}
