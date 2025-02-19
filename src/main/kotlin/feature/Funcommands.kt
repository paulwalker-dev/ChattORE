package chattore.feature

import chattore.*
import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import com.velocitypowered.api.command.CommandManager
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import org.slf4j.Logger

fun createFunCommandsFeature(
    logger: Logger,
    messenger: Messenger,
    commandManager: CommandManager,
): Feature {
    val commands = Json.decodeFromString<List<FunCommand>>(loadResource("/commands.json"))
    loadFunCommands(logger, messenger, commandManager, commands)
    return Feature(
        commands = listOf(FunCommandsCommand(commands))
    )
}

@CommandAlias("funcommands|fc")
@CommandPermission("chattore.funcommands")
class FunCommandsCommand(
    private val commands: List<FunCommand>,
) : BaseCommand() {

    @Default
    @Description("Displays information about the /funcommands command")
    fun onDefault(player: Player) {
        player.sendMessage("<green>FunCommands v1.1 by <gold>Waffle [Wueffi]</gold></green>".render())
    }

    @Subcommand("list")
    @Description("Lists all available fun commands in alphabetical order")
    fun onList(player: Player) {
        if (commands.isEmpty()) {
            player.sendMessage("<red>No fun commands found.</red>".render())
            return
        }

        player.sendMessage("<yellow>Available Fun Commands:</yellow>".render())

        // NOTE: interpolating like this is generally unsafe. We trust the commands.json file contents so this is fine.
        // same applies to the info subcommand
        commands
            .sortedBy { it.command }
            .map {
                "<hover:show_text:'${it.description}'><click:suggest_command:'/${it.command}'>/${it.command}".render()
            }
            .let { Component.join(JoinConfiguration.spaces(), it) }
            .let(player::sendMessage)
    }

    @Subcommand("info")
    @Description("Displays information about a specific fun command")
    @Syntax("<command>")
    fun onInfo(player: Player, commandName: String) {
        if (commandName.isEmpty()) {
            throw ChattoreException("You must specify a command.")
        }

        val cmd = commands.find { it.command.equals(commandName, ignoreCase = true) }
            ?: throw ChattoreException("Command '$commandName' not found.")

        player.sendMessage(
            "<gold>Description for <yellow>/${cmd.command}</yellow>: ${cmd.description}></gold>".render()
        )
    }
}


@Serializable
data class FunCommand(
    val command: String,
    val description: String,
    // message to sender only
    val localChat: String? = null,
    // broadcast to all
    val globalChat: String? = null,
    // send to everyone except the sender
    val othersChat: String? = null,
    // execute action
    val run: String? = null,
)

fun loadFunCommands(
    logger: Logger,
    messenger: Messenger,
    commandManager: CommandManager,
    commands: List<FunCommand>,
) {
    fun executeAction(action: String, player: Player) {
        when {
            action.startsWith("kick") -> {
                val reason = action.removePrefix("kick").trim()
                player.disconnect(Component.text(reason))
            }

            action.startsWith("kill") -> {
                commandManager.executeAsync(player, "suicide")
            }
        }
    }

    fun createDynamicCommand(cmd: FunCommand): SimpleCommand {
        return SimpleCommand { invocation ->
            val source = invocation.source()
            val args = invocation.arguments()

            if (source !is Player) {
                source.sendMessage(Component.text("This command can only be used by players!"))
                return@SimpleCommand
            }

            val replacements = mapOf(
                "name" to source.username,
                "arg-all" to args.joinToString(" "),
                "arg-1" to (args.getOrNull(1) ?: "<missing>"),
                "arg-2" to (args.getOrNull(2) ?: "<missing>")
            ).mapValues { it.value.toComponent() }

            cmd.globalChat?.render(replacements)?.let(messenger::broadcast)
            cmd.localChat?.render(replacements)?.let(source::sendMessage)
            cmd.othersChat?.render(replacements)?.let { messenger.broadcastAllBut(it, source) }
            cmd.run?.let { executeAction(it, source) }
        }
    }

    commands.forEach { commandConfig ->
        val meta = commandManager.metaBuilder(commandConfig.command).build()
        commandManager.register(meta, createDynamicCommand(commandConfig))
    }
    logger.info("Loaded ${commands.size} fun commands")
}
