package chattore.feature

import chattore.*
import chattore.render
import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import co.aikar.commands.annotation.Optional
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import java.util.*

data class NicknameConfig(
    val format: String = "<gold>[</gold><red>ChattORE</red><gold>]</gold> <red><message></red>",
    val clearNicknameOnChange: Boolean = true,
    val presets: SortedMap<String, String>,
)

fun createNicknameFeature(
    plugin: ChattORE,
    config: NicknameConfig
): Feature {
    plugin.database.updateLocalUsernameCache()
    return Feature(
        commands = listOf(Nickname(plugin, config)),
        listeners = listOf(NicknameListener(plugin, config)),
    )
}

val hexColorMap = mapOf(
    "0" to Pair("#000000", "black"),
    "1" to Pair("#00AA00", "dark_blue"),
    "2" to Pair("#00AA00", "dark_green"),
    "3" to Pair("#00AAAA", "dark_aqua"),
    "4" to Pair("#AA0000", "dark_red"),
    "5" to Pair("#AA00AA", "dark_purple"),
    "6" to Pair("#FFAA00", "gold"),
    "7" to Pair("#AAAAAA", "gray"),
    "8" to Pair("#555555", "dark_gray"),
    "9" to Pair("#5555FF", "blue"),
    "a" to Pair("#55FF55", "green"),
    "b" to Pair("#55FFFF", "aqua"),
    "c" to Pair("#FF5555", "red"),
    "d" to Pair("#FF55FF", "light_purple"),
    "e" to Pair("#FFFF55", "yellow"),
    "f" to Pair("#FFFFFF", "white")
)

val hexPattern = """#[0-f]{6}""".toRegex()

fun String.validateColor() = if (this.startsWith("&")) {
    if (this.length != 2) {
        throw ChattoreException("When providing legacy color codes, use a single character after &.")
    }
    val code = hexColorMap[this.substring(1)]
        ?: throw ChattoreException("Invalid color code provided")
    code.second
} else if (hexPattern.matches(this)) {
    this
} else if (this in hexColorMap.values.map { it.second }) {
    this
} else {
    throw ChattoreException("Invalid color code provided")
}

@CommandAlias("nick|nickname")
@Description("Manage nicknames")
@CommandPermission("chattore.nick")
class Nickname(
    private val plugin: ChattORE,
    private val config: NicknameConfig
) : BaseCommand() {

    @Subcommand("color")
    @CommandCompletion("@colors")
    fun set(player: Player, vararg colors: String) {
        if (colors.isEmpty()) throw ChattoreException("No colors provided! Please provide 1 to 3 colors!")
        val rendered = if (colors.size == 1) {
            val color = colors.first().validateColor()
            val nickname = "<color:$color><username></color:$color>"
            plugin.database.setNickname(player.uniqueId, nickname)
            nickname
        } else {
            if (colors.size > 3) throw ChattoreException("Too many colors!")
            setNicknameGradient(player.uniqueId, *colors)
        }
        val response = config.format.render(
            "Your nickname has been set to $rendered".render(mapOf(
                "username" to Component.text(player.username)
            ))
        )
        player.sendMessage(response)
    }

    @Subcommand("preset")
    @CommandPermission("chattore.nick.preset")
    @CommandCompletion("@nickPresets")
    fun preset(player: Player, preset: String) {
        val format = config.presets[preset]
            ?: throw ChattoreException("Unknown preset! Use /nick presets to see available presets.")
        val rendered = format.render(mapOf("username" to Component.text(player.username)))
        plugin.database.setNickname(player.uniqueId, format)
        val response = config.format.render(
            "Your nickname has been set to <message>".render(rendered)
        )
        player.sendMessage(response)
    }

    @Subcommand("presets")
    @CommandPermission("chattore.nick.preset")
    @CommandCompletion("@username")
    fun presets(player: Player, @Optional shownText: String?) {
        val renderedPresets = ArrayList<Component>()
        for ((presetName, preset) in config.presets) {
            val applyPreset: (String) -> Component = {
                preset.render(mapOf(
                    "username" to Component.text(it)
                ))
            }
            val rendered = if (shownText == null) {
                // Primarily show the preset name, else a preview of the nickname.
                "<hover:show_text:'Click to apply <username>'><preset></hover>"
            } else {
                // Primarily show the entered text, else the preset name.
                // Also, we're suggesting the username as the autocompleted $shownText.
                "<hover:show_text:'Click to apply <preset> preset'><custom></hover>"
            }.render(mapOf(
                "username" to applyPreset(player.username),
                "preset" to applyPreset(presetName),
                "custom" to applyPreset(shownText ?: "")
            )).let {
                "<click:run_command:'/nick preset $presetName'><message></click>".render(it)
            }
            renderedPresets.add(rendered)
        }

        val response = config.format.render(
            "Available presets: <message>".render(
                Component.join(JoinConfiguration.commas(true), renderedPresets)
            )
        )
        player.sendMessage(response)
    }

    @Subcommand("nick")
    @CommandPermission("chattore.nick.others")
    @CommandCompletion("@usernameCache")
    fun nick(commandSource: CommandSource, @Single target: String, @Single nick: String) {
        val targetUuid = plugin.fetchUuid(target)
            ?: throw ChattoreException("Invalid user!")
        val nickname = if (nick.contains("&")) {
            nick.legacyDeserialize().miniMessageSerialize()
        } else {
            nick
        }
        plugin.database.setNickname(targetUuid, nickname)
        sendPlayerNotifications(target, commandSource, targetUuid, nickname)
    }

    @Subcommand("remove")
    @CommandPermission("chattore.nick.remove")
    @CommandCompletion("@usernameCache")
    fun remove(commandSource: CommandSource, @Single target: String) {
        val targetUuid = plugin.fetchUuid(target)
            ?: throw ChattoreException("Invalid user!")
        plugin.database.removeNickname(targetUuid)
        val response = config.format.render(
            "Removed nickname for $target."
        )
        commandSource.sendMessage(response)
    }

    @Subcommand("setgradient")
    @CommandPermission("chattore.nick.setgradient")
    @CommandCompletion("@usernameCache")
    fun setgradient(player: Player, @Single target: String, vararg colors: String) {
        if (colors.size < 2) throw ChattoreException("Not enough colors!")
        val targetUuid = plugin.fetchUuid(target)
            ?: throw ChattoreException("Invalid user!")
        val rendered = setNicknameGradient(targetUuid, target, *colors)
        sendPlayerNotifications(target, player, targetUuid, rendered)
    }

    private fun sendPlayerNotifications(target: String, executor: CommandSource, targetUuid: UUID, rendered: String) {
        val response = config.format.render(
            "Set nickname for $target as $rendered.".render(mapOf(
                "username" to Component.text(target)
            ))
        )
        executor.sendMessage(response)
        plugin.proxy.getPlayer(targetUuid).ifPresent {
            it.sendMessage(
                config.format.render(
                    "Your nickname has been set to $rendered".render(mapOf(
                        "username" to Component.text(target)
                    ))
                )
            )
        }
    }

    private fun setNicknameGradient(uniqueId: UUID, vararg colors: String): String {
        val codes = colors.joinToString(":") { it.validateColor() }
        val nickname = "<gradient:$codes><username></gradient>"
        plugin.database.setNickname(uniqueId, nickname)
        return nickname
    }
}

class NicknameListener(
    private val plugin: ChattORE,
    private val config: NicknameConfig
) {
    @Subscribe
    fun joinEvent(event: LoginEvent) {
        if (!config.clearNicknameOnChange) return
        val existingName = plugin.database.uuidToUsernameCache[event.player.uniqueId] ?: return
        if (existingName == event.player.username) return
        val nickname = plugin.database.getNickname(event.player.uniqueId)
        if (nickname?.contains("<username>") == true) return
        plugin.database.removeNickname(event.player.uniqueId)
    }
}
