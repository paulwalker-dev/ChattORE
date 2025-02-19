package chattore.feature

import chattore.*
import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import com.velocitypowered.api.proxy.Player

data class EmojiConfig(
    val format: String = "<gold>[</gold><red>ChattORE</red><gold>]</gold> <red><message></red>"
)

fun createEmojiFeature(
    emojis: Map<String, String>,
    config: EmojiConfig
): Feature {
    return Feature(
        commands = listOf(Emoji(emojis, config))
    )
}

@CommandAlias("emoji")
@Description("Preview an emoji")
@CommandPermission("chattore.emoji")
class Emoji(
    private val emojis: Map<String, String>,
    private val config: EmojiConfig
) : BaseCommand() {

    @Default
    @CommandCompletion("@emojis")
    fun default(player: Player, vararg emojiNames: String) {
        if (!emojis.keys.containsAll(emojiNames.toSet())) {
            val notEmoji = emojiNames.toSet().minus(emojis.keys)
            throw ChattoreException("The following are not valid emojis: ${notEmoji.joinToString(", ")}")
        }
        val emojiMiniMessage = emojiNames.toSet().intersect(emojis.keys).joinToString(", ") {
            "<hover:show_text:$it>${emojis[it]}</hover>"
        }
        player.sendSimpleMM(config.format, "Emojis: $emojiMiniMessage")
    }
}
