package chattore.feature

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder

val urlRegex = """<?((http|https)://([\w_-]+(?:\.[\w_-]+)+)([^\s'<>]+)?)>?""".toRegex()
fun String.toComponent() = Component.text(this)
val urlMarkdownRegex = """\[([^]]*)\]\(\s?(\S+)\s?\)""".toRegex()

fun String.replaceObfuscate(canObfuscate: Boolean): String =
    if (canObfuscate) {
        this
    } else {
        this.replace("&k", "")
    }

fun String.render(
    replacements: Map<String, Component> = emptyMap()
): Component = MiniMessage.miniMessage().deserialize(
    this,
    *replacements.map { Placeholder.component(it.key, it.value) }.toTypedArray()
)
