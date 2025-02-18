package chattore.feature

import chattore.*
import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent

data class FunCommandsConfig(
    val funcommandsDefault: String = "<green>FunCommands v1.1 by <gold>Waffle [Wueffi]</gold></green>",
    val funcommandsNoCommands: String = "<red>No fun commands found.</red>",
    val funcommandsHeader: String = "<yellow>Available Fun Commands:</yellow>",
    val funcommandsCommandInfo: String = "<gold>Description for <yellow>/<command></yellow>: <description></gold>",
    val funcommandsMissingCommand: String = "<red>You must specify a command.</red>",
    val funcommandsCommandNotFound: String = "<red>Command '<command>' not found.</red>"
)

fun createFunCommandsFeature(
    plugin: ChattORE,
    config: FunCommandsConfig
): Feature {
    val resourcePath = "commands.json"
    val resourceStream = plugin.javaClass.classLoader.getResourceAsStream(resourcePath)
    if (resourceStream == null) {
        plugin.logger.warn("$resourcePath not found. Skipping fun command loading.")
        throw ChattoreException("No $resourcePath found")
    }
    val commandsJson = resourceStream.bufferedReader().use { it.readText() }
    val commands = Json.decodeFromString<List<FunCommandConfig>>(commandsJson)
    plugin.logger.info("Parsed ${commands.size} commands from JSON.")
    FunCommands(plugin, commands).loadFunCommands()
    return Feature(
        commands = listOf(FunCommandsCommand(config, commands))
    )
}

@CommandAlias("funcommands|fc")
@CommandPermission("chattore.funcommands")
class FunCommandsCommand(
    private val config: FunCommandsConfig,
    private val commands: List<FunCommandConfig>
) : BaseCommand() {

    @Default
    @Description("Displays information about the /funcommands command")
    fun onDefault(player: Player) {
        val message = config.funcommandsDefault.render(
            mapOf(
                "sender" to player.username.toComponent()
            )
        )
        player.sendMessage(message)
    }

    @Subcommand("list")
    @Description("Lists all available fun commands in alphabetical order")
    fun onList(player: Player) {
        if (commands.isEmpty()) {
            val noCommandsMessage = config.funcommandsNoCommands.render(
                mapOf(
                    "sender" to player.username.toComponent()
                )
            )
            player.sendMessage(noCommandsMessage)
            return
        }

        val headerMessage = config.funcommandsHeader.render(
            mapOf(
                "sender" to player.username.toComponent()
            )
        )
        player.sendMessage(headerMessage)

        val commandsMessage = Component.text()
        commands.sortedBy{ it.command }.forEach { cmd ->
            val clickableCommand = Component.text("/${cmd.command} ")
                .hoverEvent(
                    HoverEvent.showText(
                        Component.text(cmd.description)
                    )
                )
                .clickEvent(
                    ClickEvent.suggestCommand("/${cmd.command}")
                )

            commandsMessage.append(clickableCommand)
        }
        player.sendMessage(commandsMessage)
    }

    @Subcommand("info")
    @Description("Displays information about a specific fun command")
    @Syntax("<command>")
    fun onInfo(player: Player, commandName: String?) {
        if (commandName.isNullOrEmpty()) {
            throw ChattoreException(config.funcommandsMissingCommand.render(
                mapOf("sender" to player.username.toComponent())
            ).toString())
        }

        val commandConfig = commands.find { it.command.equals(commandName, ignoreCase = true) }
            ?: throw ChattoreException(
                config.funcommandsCommandNotFound.render(
                    mapOf("command" to commandName.toComponent())
                ).toString()
            )

        val descriptionMessage = config.funcommandsCommandInfo.render(
            mapOf(
                "command" to commandConfig.command.toComponent(),
                "description" to commandConfig.description.toComponent(),
                "sender" to player.username.toComponent()
            )
        )
        player.sendMessage(descriptionMessage)
    }
}


@Serializable
data class FunCommandConfig(
    val command: String,
    val description: String,
    val localChat: String? = null, // Optional: message to sender only
    val globalChat: String? = null, // Optional: broadcast to all
    val othersChat: String? = null, // Optional: send to everyone except the sender
    val run: String? = null // Optional: action execution
)

class FunCommands(
    private val plugin: ChattORE,
    private val commands: List<FunCommandConfig>
) {

    fun loadFunCommands() {
        commands.forEach { commandConfig ->
            val meta = plugin.proxy.commandManager.metaBuilder(commandConfig.command).build()
            plugin.proxy.commandManager.register(meta, createDynamicCommand(commandConfig))
        }

        plugin.logger.info("Loaded ${commands.size} fun commands.")
    }

    private fun createDynamicCommand(commandConfig: FunCommandConfig): SimpleCommand {
        return SimpleCommand { invocation ->
            val source = invocation.source()
            val args = invocation.arguments()

            if (source !is Player) {
                source.sendMessage(Component.text("This command can only be used by players!"))
                return@SimpleCommand
            }

            val rawGlobalMessage = commandConfig.globalChat
            val rawLocalMessage = commandConfig.localChat
            val rawOthersMessage = commandConfig.othersChat

            val renderedGlobalMessage = rawGlobalMessage?.replace("<name>", source.username)
                ?.replace("<arg-all>", args.joinToString(" "))
                ?.replace("$1", args.getOrNull(1) ?: "<missing>")
                ?.replace("$2", args.getOrNull(2) ?: "<missing>")

            val renderedLocalMessage = rawLocalMessage?.replace("<name>", source.username)
                ?.replace("<arg-all>", args.joinToString(" "))
                ?.replace("$1", args.getOrNull(1) ?: "<missing>")
                ?.replace("$2", args.getOrNull(2) ?: "<missing>")

            val renderedOthersMessage = rawOthersMessage?.replace("<name>", source.username)
                ?.replace("<arg-all>", args.joinToString(" "))
                ?.replace("$1", args.getOrNull(1) ?: "<missing>")
                ?.replace("$2", args.getOrNull(2) ?: "<missing>")

            // Handle global chat
            renderedGlobalMessage?.let {
                plugin.messenger.broadcast(it.render(mapOf(
                    "message" to renderedGlobalMessage.toComponent(),
                    "sender" to source.username.toComponent()
                )))
            }

            // Handle local chat
            renderedLocalMessage?.let {
                source.sendMessage(it.render(mapOf(
                    "message" to renderedLocalMessage.toComponent(),
                    "sender" to source.username.toComponent()
                )))
            }

            // Handle othersChat
            renderedOthersMessage?.let {
                plugin.proxy.allPlayers.filter { it != source }.forEach { targetPlayer ->
                    targetPlayer.sendMessage(it.render(mapOf(
                        "message" to renderedOthersMessage.toComponent(),
                        "sender" to source.username.toComponent()
                    )))
                }
            }

            // Log and execute additional actions
            plugin.logger.info(
                "Executed command: /${commandConfig.command} by ${source.username} with arguments: ${
                    args.joinToString(" ")
                }"
            )

            commandConfig.run?.let { executeAction(it, source) }
        }
    }

    private fun executeAction(action: String, player: Player) {
        when {
            action.startsWith("kick") -> {
                val reason = action.removePrefix("kick").trim()
                player.disconnect(Component.text(reason))
            }

            action.startsWith("kill") -> {
                plugin.proxy.commandManager.executeAsync(player, "suicide")
            }
        }
    }
}
