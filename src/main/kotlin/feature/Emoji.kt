package chattore.feature

import chattore.*
import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import com.velocitypowered.api.proxy.Player

fun createEmojiFeature(
    emojis: Map<String, String>,
): Feature {
    return Feature(
        commands = listOf(Emoji(emojis))
    )
}

@CommandAlias("emoji")
@Description("Preview an emoji")
@CommandPermission("chattore.emoji")
class Emoji(
    private val emojis: Map<String, String>,
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
        player.sendInfo("Emojis: $emojiMiniMessage")
    }
}
