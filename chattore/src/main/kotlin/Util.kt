package org.openredstone.chattore

import com.velocitypowered.api.event.EventHandler
import com.velocitypowered.api.event.EventManager
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.audience.ForwardingAudience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.legacy.CharacterAndFormat
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import java.io.FileNotFoundException
import java.util.*
import kotlin.jvm.optionals.getOrNull

val urlRegex = """<?((http|https)://([\w_-]+(?:\.[\w_-]+)+)([^\s'<>]+)?)>?""".toRegex()
val urlMarkdownRegex = """\[([^]]*)]\(\s?(\S+)\s?\)""".toRegex()
val uuidRegex = """[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""".toRegex()

fun String.toComponent() = Component.text(this)
fun Component.miniMessageSerialize() = MiniMessage.miniMessage().serialize(this)

private val legacyNoObfuscate = LegacyComponentSerializer.builder()
    .character('&')
    .formats(CharacterAndFormat.defaults().filter { it != CharacterAndFormat.OBFUSCATED })
    .build()

fun String.legacyDeserialize(canObfuscate: Boolean = false): TextComponent {
    // remove section signs so the client doesn't render them. ew
    val noSections = replace('§', '&')
    return if (canObfuscate) {
        LegacyComponentSerializer.legacyAmpersand().deserialize(noSections)
    } else {
        legacyNoObfuscate.deserialize(noSections)
    }
}

fun String.render(vararg resolvers: TagResolver): Component =
    MiniMessage.miniMessage().deserialize(this, *resolvers)

// Suffixes:
// - S: String
// - C: Component
// - MM: MiniMessage

// Convenience functions for constructing TagResolvers that act as placeholders
infix fun String.toS(message: String) = Placeholder.unparsed(this, message)
infix fun String.toC(message: Component) = Placeholder.component(this, message)
infix fun String.toMM(message: String) = toC(message.render())

// The "simple" functions take a MiniMessage with a "<message>" placeholder and fill that with the argument
fun String.renderSimpleC(message: Component): Component = render("message" toC message)

fun Audience.sendSimpleC(format: String, message: Component) = sendMessage(format.renderSimpleC(message))
fun Audience.sendSimpleS(format: String, message: String) = sendSimpleC(format, message.toComponent())
fun Audience.sendSimpleMM(format: String, message: String) = sendSimpleC(format, message.render())

private const val infoFormat = "<gold>[</gold><red>ChattORE</red><gold>]</gold> <red><message></red>"
fun Audience.sendInfo(message: String) = sendSimpleC(infoFormat, message.toComponent())
fun Audience.sendInfoMM(message: String, vararg resolvers: TagResolver) =
    sendSimpleC(infoFormat, message.render(*resolvers))

/** Mirrors Player.sendRichMessage **/
fun Audience.sendRichMessage(message: String, vararg resolvers: TagResolver) = sendMessage(message.render(*resolvers))

fun ProxyServer.playerOrNull(uuid: UUID): Player? = getPlayer(uuid).getOrNull()

/*** Convenience ***/
class PluginEvents(private val plugin: Any, private val eventManager: EventManager) {
    fun <E> register(eventClass: Class<E>, postOrder: Short, handler: EventHandler<E>) =
        eventManager.register(plugin, eventClass, postOrder, handler)

    fun registerAll(listener: Any) = eventManager.register(plugin, listener)
}

inline fun <reified T> PluginEvents.register(postOrder: Short = 0, noinline handler: (T) -> Unit): EventHandler<T> =
    EventHandler(handler).also { register(T::class.java, postOrder, it) }

/***
 * Loads a resource file [filename] as a String.
 * Absolute paths recommended.
 * Throws FileNotFoundException if not found.
 */
fun loadResource(filename: String) =
    Dummy::class.java.getResource(filename)?.readText()
        ?: throw FileNotFoundException("Cannot load resource file $filename. This is probably a bug.")

object Dummy

fun parseUuid(input: String): UUID? = if (uuidRegex.matches(input)) UUID.fromString(input) else null

/** Dynamic audience consisting of only those players on proxy who pass the test */
fun ProxyServer.all(test: (Player) -> Boolean): Audience =
    ForwardingAudience { allPlayers.filter(test) }

fun ProxyServer.allBut(player: Player) = all { it != player }
val ProxyServer.all get() = ForwardingAudience { allPlayers }

val Player.hasChattorePrivilege get() = hasPermission("chattore.privileged")
