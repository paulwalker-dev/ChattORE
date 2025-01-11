package chattore


import com.uchuhimo.konf.Config
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.kyori.adventure.text.Component
import org.slf4j.Logger

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
    private val proxy: ProxyServer,
    private val logger: Logger,
    private val chattORE: ChattORE,
    private val commands: List<FunCommandConfig>
) {

    fun loadFunCommands() {
        commands.forEach { commandConfig ->
            val meta = proxy.commandManager.metaBuilder(commandConfig.command).build()
            proxy.commandManager.register(meta, createDynamicCommand(commandConfig))
        }

        logger.info("Loaded ${commands.size} fun commands.")
    }

    private fun createDynamicCommand(commandConfig: FunCommandConfig): SimpleCommand {
        return SimpleCommand { invocation ->
            val source = invocation.source()
            val args = invocation.arguments()

            if (source !is Player) {
                source.sendMessage(Component.text("This command can only be used by players!"))
                return@SimpleCommand
            }

            val player = source
            val rawGlobalMessage = commandConfig.globalChat
            val rawLocalMessage = commandConfig.localChat
            val rawOthersMessage = commandConfig.othersChat

            val renderedGlobalMessage = rawGlobalMessage?.replace("<name>", player.username)
                ?.replace("<arg-all>", args.joinToString(" "))
                ?.replace("$1", args.getOrNull(1) ?: "<missing>")
                ?.replace("$2", args.getOrNull(2) ?: "<missing>")

            val renderedLocalMessage = rawLocalMessage?.replace("<name>", player.username)
                ?.replace("<arg-all>", args.joinToString(" "))
                ?.replace("$1", args.getOrNull(1) ?: "<missing>")
                ?.replace("$2", args.getOrNull(2) ?: "<missing>")

            val renderedOthersMessage = rawOthersMessage?.replace("<name>", player.username)
                ?.replace("<arg-all>", args.joinToString(" "))
                ?.replace("$1", args.getOrNull(1) ?: "<missing>")
                ?.replace("$2", args.getOrNull(2) ?: "<missing>")

            // Handle global chat
            if (rawGlobalMessage != null) {
                chattORE.broadcast(
                    renderedGlobalMessage!!.render(
                        mapOf(
                            "message" to renderedGlobalMessage.toComponent(),
                            "sender" to player.username.toComponent()
                        )
                    )
                )
            }

            // Handle local chat
            if (rawLocalMessage != null) {
                player.sendMessage(
                    renderedLocalMessage!!.render(
                        mapOf(
                            "message" to renderedLocalMessage.toComponent(),
                            "sender" to player.username.toComponent()
                        )
                    )
                )
            }

            // Handle othersChat
            if (rawOthersMessage != null) {
                proxy.allPlayers.filter { it != player }.forEach { targetPlayer ->
                    targetPlayer.sendMessage(
                        renderedOthersMessage!!.render(
                            mapOf(
                                "message" to renderedOthersMessage.toComponent(),
                                "sender" to player.username.toComponent()
                            )
                        )
                    )
                }
            }

            // Log and execute additional actions
            chattORE.logger.info(
                "Executed command: /${commandConfig.command} by ${player.username} with arguments: ${
                    args.joinToString(" ")
                }"
            )

            commandConfig.run?.let { executeAction(it, player) }
        }
    }

    private fun executeAction(action: String, player: Player) {
        when {
            action.startsWith("kick") -> {
                val reason = action.removePrefix("kick").trim()
                player.disconnect(Component.text(reason))
            }

            action.startsWith("kill") -> {
                proxy.commandManager.executeAsync(player, "suicide")
            }
        }
    }
}
