package chattore.feature

import chattore.Feature
import chattore.Messenger
import chattore.render
import chattore.toComponent
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.command.CommandExecuteEvent
import com.velocitypowered.api.proxy.Player

data class SpyingConfig(
    val format: String = "<gold><sender>: <message>",
)

fun createSpyingFeature(
    messenger: Messenger,
    config: SpyingConfig,
): Feature {
    return Feature(
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
            config.format.render(
                mapOf(
                    "message" to event.command.toComponent(),
                    "sender" to ((event.commandSource as? Player)?.username ?: "Console").toComponent()
                )
            )
        )
    }
}
