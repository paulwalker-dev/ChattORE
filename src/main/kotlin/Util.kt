package chattore

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextReplacementConfig
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import java.io.FileNotFoundException
import java.util.*

val urlRegex = """<?((http|https)://([\w_-]+(?:\.[\w_-]+)+)([^\s'<>]+)?)>?""".toRegex()
val urlMarkdownRegex = """\[([^]]*)]\(\s?(\S+)\s?\)""".toRegex()
val uuidRegex = """[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""".toRegex()

fun String.toComponent() = Component.text(this)
fun fixHexFormatting(str: String): String = str.replace(Regex("#([0-9a-f]{6})")) { "&${it.groupValues.first()}" }
fun String.legacyDeserialize() = LegacyComponentSerializer.legacy('&').deserialize(this)
fun Component.miniMessageSerialize() = MiniMessage.miniMessage().serialize(this)

fun String.componentize(): Component =
    LegacyComponentSerializer.builder()
        .character('&')
        .hexCharacter('#')
        .extractUrls()
        .build()
        .deserialize(fixHexFormatting(this))

fun buildEmojiReplacement(emojis: Map<String, String>): TextReplacementConfig =
    TextReplacementConfig.builder()
        .match(""":([A-Za-z0-9_\-+]+):""")
        .replacement { result, _ ->
            val match = result.group(1)
            val content = emojis[match] ?: ":$match:"
            "<hover:show_text:'$match'>$content</hover>".render()
        }
        .build()

fun formatReplacement(key: String, tag: String): TextReplacementConfig =
    TextReplacementConfig.builder()
        .match("""((\\?)(${Regex.escape(key)}(.*?)${Regex.escape(key)}))""")
        .replacement { result, _ ->
            if (result.group(2).contains("\\") || result.group(4).endsWith("\\")) {
                result.group(3).toComponent()
            } else {
                "<$tag>${result.group(4)}</$tag>".render()
            }
        }
        .build()

fun String.renderSimpleC(message: Component): Component = render("message" toC message)

fun String.render(vararg resolvers: TagResolver): Component =
    MiniMessage.miniMessage().deserialize(this, *resolvers)

// IDK how dumb this is. we'll see and adjust if necessary
infix fun String.toS(message: String) = Placeholder.unparsed(this, message)
infix fun String.toC(message: Component) = Placeholder.component(this, message)
infix fun String.toMM(message: String) = toC(message.render())

fun Audience.sendSimpleC(format: String, message: Component) = sendMessage(format.render("message" toC message))
fun Audience.sendSimpleS(format: String, message: String) = sendSimpleC(format, message.toComponent())
fun Audience.sendSimpleMM(format: String, message: String) = sendSimpleC(format, message.render())

/***
 * Loads a resource file [filename] as a String.
 * Make sure you pass in an absolute path.
 * Throws FileNotFoundException if not found.
 */
fun loadResource(filename: String) =
    Any::class.java.getResource(filename)?.readText() ?: throw FileNotFoundException(filename)

fun parseUuid(input: String): UUID? = if (uuidRegex.matches(input)) UUID.fromString(input) else null
