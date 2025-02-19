package chattore

import chattore.feature.DiscordBroadcastEvent
import chattore.feature.SpyEnabled
import com.velocitypowered.api.proxy.Player
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextReplacementConfig
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.net.URL
import java.util.*

class Messenger(
    private val plugin: ChattORE,
    val format: String
) {
    private var fileTypeMap: Map<String, List<String>> = hashMapOf()
    init {
        plugin.javaClass.getResourceAsStream("/filetypes.json")?.let { inputStream ->
            val jsonElement = Json.parseToJsonElement(inputStream.reader().readText())
            fileTypeMap = jsonElement.jsonObject.mapValues { (_, value) ->
                value.jsonArray.map { it.jsonPrimitive.content }
            }
            fileTypeMap.forEach { (key, values) ->
                plugin.logger.info("Loaded ${values.size} of type $key")
            }
        }
    }

    fun sendPrivileged(component: Component, exclude: UUID? = null, ignorable: Boolean = true) {
        val privileged = plugin.proxy.allPlayers.filter {
            it.hasPermission("chattore.privileged")
                && (it.uniqueId != exclude)
        }
        for (user in privileged) {
            if (ignorable) {
                val setting = plugin.database.getSetting(SpyEnabled, user.uniqueId)
                val spying = setting ?: false
                if (spying) {
                    user.sendMessage(component)
                }
            } else {
                user.sendMessage(component)
            }
        }
    }

    fun broadcastChatMessage(originServer: String, player: Player, message: String) {
        val userId = player.uniqueId
        val userManager = plugin.luckPerms.userManager
        val luckUser = userManager.getUser(userId) ?: return
        val name = plugin.database.getNickname(userId) ?: player.username
        val sender = name.render(
            mapOf(
                "username" to player.username.toComponent()
            )
        ).let {
            "<click:run_command:'/playerprofile info ${player.username}'><message></click>".render(it)
        }

        val prefix = luckUser.cachedData.metaData.prefix
            ?: luckUser.primaryGroup.replaceFirstChar(Char::uppercaseChar)

        broadcast(
            format.render(
                mapOf(
                    "message" to prepareChatMessage(message, player),
                    "sender" to sender,
                    "username" to Component.text(player.username),
                    "prefix" to prefix.legacyDeserialize(),
                )
            )
        )

        val plainPrefix = PlainTextComponentSerializer.plainText().serialize(prefix.componentize())
        val discordBroadcast = DiscordBroadcastEvent(
            plainPrefix,
            player.username,
            originServer,
            message
        )
        plugin.proxy.eventManager.fire(discordBroadcast).thenAccept { /* shrug */ }
    }

    fun prepareChatMessage(
        message: String,
        player: Player?,
    ): Component {
        val canObfuscate = player?.hasPermission("chattore.chat.obfuscate") ?: false
        val parts = urlRegex.split(message)
        val matches = urlRegex.findAll(message).iterator()
        val builder = Component.text()
        parts.forEach { part ->
            builder.append(part.replaceObfuscate(canObfuscate).legacyDeserialize())
            if (matches.hasNext()) {
                val nextMatch = matches.next()
                val link = URL(nextMatch.groupValues[1])
                var type = "link"
                var name = link.host
                if (link.file.isNotEmpty()) {
                    val last = link.path.split("/").last()
                    if (last.contains('.') && !last.endsWith('.') && !last.startsWith('.')) {
                        type = last.split('.').last()
                        name = if (last.length > 15) {
                            last.substring(0, 15) + "…." + type
                        } else {
                            last
                        }
                    }
                }
                val contentType = plugin.fileTypeMap.entries.find { type in it.value }?.key
                val symbol = when (contentType) {
                    "IMAGE" -> "\uD83D\uDDBC"
                    "AUDIO" -> "\uD83D\uDD0A"
                    "VIDEO" -> "\uD83C\uDFA5"
                    "TEXT" -> "\uD83D\uDCDD"
                    else -> "\uD83D\uDCCE"
                }
                builder.append(("<aqua><click:open_url:'$link'>" +
                    "<hover:show_text:'<aqua>$link'>" +
                    "[$symbol $name]" +
                    "</hover>" +
                    "</click><reset>").miniMessageDeserialize())
            }
        }
        return builder.build().performReplacements(plugin.chatReplacements)
    }

    private fun Component.performReplacements(replacements: List<TextReplacementConfig>): Component {
        var result: Component = this
        replacements.forEach { replacement ->
            result = result.replaceText(replacement)
        }
        return result
    }

    fun broadcastAllBut(component: Component, player: Player) {
        plugin.proxy.allPlayers.filter { it != player }.forEach { it.sendMessage(component) }
    }

    fun broadcast(component: Component) {
        plugin.proxy.allPlayers.forEach { it.sendMessage(component) }
    }

    fun sendIndividual(component: Component, target: Player) {
        target.sendMessage(component)
    }
}
