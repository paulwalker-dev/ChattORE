package chattore.feature

import chattore.*
import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import com.velocitypowered.api.proxy.Player

data class EmojiConfig(
    val format: String = "<gold>[</gold><red>ChattORE</red><gold>]</gold> <red><message></red>"
)

fun createEmojiFeature(
    plugin: ChattORE,
    config: EmojiConfig
): Feature {
    return Feature(
        commands = listOf(Emoji(plugin, config))
    )
}

@CommandAlias("emoji")
@Description("Preview an emoji")
@CommandPermission("chattore.emoji")
class Emoji(
    private val plugin: ChattORE,
    private val config: EmojiConfig
) : BaseCommand() {

    @Default
    @CommandCompletion("@emojis")
    fun default(player: Player, vararg emojiNames: String) {
        if (!plugin.emojis.keys.containsAll(emojiNames.toSet())) {
            val notEmoji = emojiNames.toSet().minus(plugin.emojis.keys)
            throw ChattoreException("The following are not valid emojis: ${notEmoji.joinToString(", ")}")
        }
        val emojiMiniMessage = emojiNames.toSet().intersect(plugin.emojis.keys).joinToString(", ") {
            "<hover:show_text:${it}>${plugin.emojis[it]}</hover>"
        }
        player.sendMessage(
            config.format.render(
                "Emojis: $emojiMiniMessage".miniMessageDeserialize()
            )
        )
    }
}
