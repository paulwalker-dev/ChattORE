package chattore

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextReplacementConfig
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

fun fixHexFormatting(str: String): String = str.replace(Regex("#([0-9a-f]{6})")) { "&${it.groupValues.first()}" }

fun String.componentize(): Component =
    LegacyComponentSerializer.builder()
        .character('&')
        .hexCharacter('#')
        .extractUrls()
        .build()
        .deserialize(fixHexFormatting(this))

fun String.legacyDeserialize() = LegacyComponentSerializer.legacy('&').deserialize(this)
fun String.miniMessageDeserialize() = MiniMessage.miniMessage().deserialize(this)
fun Component.miniMessageSerialize() = MiniMessage.miniMessage().serialize(this)
fun String.toComponent() = Component.text(this)
fun String.discordEscape() = this.replace("""_""", "\\_")

fun buildEmojiReplacement(emojis: Map<String, String>): TextReplacementConfig =
    TextReplacementConfig.builder()
        .match(""":([A-Za-z0-9_\-+]+):""")
        .replacement { result, _ ->
            val match = result.group(1)
            val content = emojis[match] ?: ":$match:"
            "<hover:show_text:'$match'>$content</hover>".miniMessageDeserialize()
        }
        .build()

fun formatReplacement(key: String, tag: String): TextReplacementConfig =
    TextReplacementConfig.builder()
        .match("""((\\?)(${Regex.escape(key)}(.*?)${Regex.escape(key)}))""")
        .replacement { result, _ ->
            if (result.group(2).contains("\\") || result.group(4).endsWith("\\")) {
                result.group(3).toComponent()
            } else {
                "<$tag>${result.group(4)}</$tag>".miniMessageDeserialize()
            }
        }
        .build()

fun Component.performReplacements(replacements: List<TextReplacementConfig>): Component {
    var result: Component = this
    replacements.forEach { replacement ->
        result = result.replaceText(replacement)
    }
    return result
}

fun String.render(
    message: String
): Component = this.render(
    mapOf("message" to Component.text(message))
)

fun String.render(
    message: Component,
): Component = this.render(
    mapOf("message" to message)
)

fun String.render(
    replacements: Map<String, Component> = emptyMap()
): Component = MiniMessage.miniMessage().deserialize(
    this,
    *replacements.map { Placeholder.component(it.key, it.value) }.toTypedArray()
)
