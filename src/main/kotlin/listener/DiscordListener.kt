package chattore.listener

import chattore.ChattORE
import chattore.entity.ChattORESpec
import org.javacord.api.event.message.MessageCreateEvent
import org.javacord.api.listener.message.MessageCreateListener

class DiscordListener(
    private val chattORE: ChattORE,
    private val emojisToNames: Map<String, String>
) : MessageCreateListener {

    private val emojiPattern = emojisToNames.keys.joinToString("|", "(", ")") { Regex.escape(it) }
    private val emojiRegex = Regex(emojiPattern)

    private fun replaceEmojis(input: String): String {
        return emojiRegex.replace(input) { matchResult ->
            val emoji = matchResult.value
            val emojiName = emojisToNames[emoji]
            if (emojiName != null) ":$emojiName:" else emoji
        }
    }

    override fun onMessageCreate(event: MessageCreateEvent) {
        if (event.messageAuthor.isBotUser && event.messageAuthor.id != chattORE.config[ChattORESpec.discord.chadId]) return
        val attachments = event.messageAttachments.joinToString(" ", " ") { it.url.toString() }
        val toSend = replaceEmojis(event.message.readableContent) + attachments
        chattORE.logger.info("[Discord] ${event.messageAuthor.displayName} (${event.messageAuthor.id}): $toSend")
        chattORE.broadcastDiscordMessage(event.messageAuthor.displayName, toSend)
    }
}
