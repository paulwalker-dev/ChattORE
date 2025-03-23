package chattore.feature

import chattore.ChattORE
import chattore.Feature
import chattore.toComponent
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import org.openredstone.chattore.common.AliasMessage
import org.openredstone.chattore.common.ALIAS_CHANNEL

val IDENTIFIER: MinecraftChannelIdentifier = MinecraftChannelIdentifier.from(ALIAS_CHANNEL)

val argumentsRegex = Regex("\\\$(args|[0-9])")

@Serializable
data class AliasConfig(
    val alias: String = "alias",
    val commands: List<String> = listOf("some", "commands"),
)

fun createAliasFeature(
    plugin: ChattORE
): Feature {
    fun String.toCommandAliasMeta() = plugin.proxy.commandManager.metaBuilder(this)
        .plugin(plugin)
        .build()
    val resourcePath = "aliases.json"
    val resourceBytes = plugin.loadResource(resourcePath)
    val aliasesJson = resourceBytes.decodeToString()
    val aliases = Json.decodeFromString<List<AliasConfig>>(aliasesJson)
    aliases.forEach { (alias, commands) ->
        val meta = alias.toCommandAliasMeta()
        val aliasCommand = AliasCommand(plugin, alias, commands)
        plugin.proxy.commandManager.register(meta, aliasCommand)
    }
    plugin.logger.info("Loaded ${aliases.size} aliases")
    return Feature(listeners = listOf(ClientAliasListener()))
}

class AliasCommand(
    private val plugin: ChattORE,
    private val base: String,
    private val commands: List<String>
) : SimpleCommand {

    private val requiredArgs = commands.maxOfOrNull { argumentsRegex.findAll(it).toList().size } ?: 0

    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        val args = invocation.arguments()

        if (requiredArgs > args.size) {
            source.sendMessage("Not enough arguments! I expected $requiredArgs argument(s)!".toComponent())
            return
        }

        if (source !is Player) {
            source.sendMessage("This command can only be used by players!".toComponent())
            return
        }

        if (args.isEmpty()) {
            plugin.logger.info("Executing alias $base for user ${source.username} (${source.uniqueId})")
        } else {
            plugin.logger.info("Executing alias $base for user ${source.username}" +
                " (${source.uniqueId}) with args: ${args.joinToString(" ")}")
        }
        executeAlias(source, args.toList())
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun executeAlias(player: Player, args: List<String> = emptyList()) {
        val server = player.currentServer.orElse(null) ?: return
        commands.forEach { command ->
            val commandLine = command.replace(argumentsRegex) {matchResult ->
                val group = matchResult.groupValues[1]
                if (group == "args") {
                    args.joinToString(" ")
                } else {
                    args[group.toInt()]
                }
            }
            plugin.proxy.commandManager.executeImmediatelyAsync(player, commandLine).whenComplete { result, throwable ->
                if (throwable != null) {
                    plugin.logger.error(throwable.message, throwable)
                }
                if (result) {
                    plugin.logger.info("Ran \"$commandLine\" on the proxy")
                    return@whenComplete
                }
                plugin.logger.info("Forwarding \"$commandLine\" to game server")
                server.sendPluginMessage(
                    IDENTIFIER,
                    Cbor.encodeToByteArray(AliasMessage(player.uniqueId, commandLine))
                )
            }.join()  // Hack, but guarantees execution order, assuming plugin messages are processed sequentially
        }
    }
}

class ClientAliasListener {
    @Subscribe
    fun onPluginMessageFromPlayer(message: PluginMessageEvent) {
        if (IDENTIFIER != message.identifier) {
            return
        }
        message.result = PluginMessageEvent.ForwardResult.handled()
    }
}
