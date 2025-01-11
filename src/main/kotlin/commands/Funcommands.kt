package chattore.commands

import chattore.ChattORE
import chattore.FunCommandConfig
import chattore.ChattoreException
import com.uchuhimo.konf.Config
import com.velocitypowered.api.proxy.Player
import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import chattore.entity.ChattORESpec
import chattore.toComponent
import chattore.render
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent

@CommandAlias("funcommands|fc")
@CommandPermission("chattore.funcommands")
class Funcommands(
    private val config: Config,
    private val commands: List<FunCommandConfig>
) : BaseCommand() {

    @Default
    @Description("Displays information about the /funcommands command")
    fun onDefault(player: Player) {
        val message = config[ChattORESpec.format.funcommandsDefault].render(
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
            val noCommandsMessage = config[ChattORESpec.format.funcommandsNoCommands].render(
                mapOf(
                    "sender" to player.username.toComponent()
                )
            )
            player.sendMessage(noCommandsMessage)
            return
        }

        val headerMessage = config[ChattORESpec.format.funcommandsHeader].render(
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
            throw ChattoreException(config[ChattORESpec.format.funcommandsMissingCommand].render(
                mapOf("sender" to player.username.toComponent())
            ).toString())
        }

        val commandConfig = commands.find { it.command.equals(commandName, ignoreCase = true) }
            ?: throw ChattoreException(
                config[ChattORESpec.format.funcommandsCommandNotFound].render(
                    mapOf("command" to commandName.toComponent())
                ).toString()
            )

        val descriptionMessage = config[ChattORESpec.format.funcommandsCommandInfo].render(
            mapOf(
                "command" to commandConfig.command.toComponent(),
                "description" to commandConfig.description.toComponent(),
                "sender" to player.username.toComponent()
            )
        )
        player.sendMessage(descriptionMessage)
    }
}
