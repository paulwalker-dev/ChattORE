package chattore

import co.aikar.commands.BaseCommand
import co.aikar.commands.CommandIssuer
import co.aikar.commands.RegisteredCommand
import co.aikar.commands.VelocityCommandManager
import com.google.inject.Inject
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.source.yaml
import com.uchuhimo.konf.source.yaml.toYaml
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Dependency
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import chattore.commands.*
import chattore.entity.ChattORESpec
import chattore.listener.ChatListener
import chattore.listener.DiscordListener
import com.velocitypowered.api.proxy.Player
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextReplacementConfig
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.luckperms.api.LuckPerms
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.model.user.User
import org.javacord.api.DiscordApi
import org.javacord.api.DiscordApiBuilder
import org.javacord.api.entity.message.MessageBuilder
import org.slf4j.Logger
import java.io.File
import java.net.URL
import java.nio.file.Path
import java.util.*

private const val VERSION = "0.1.0-SNAPSHOT"

@Plugin(
    id = "chattore",
    name = "ChattORE",
    version = VERSION,
    url = "https://openredstone.org",
    description = "Because we want to have a chat system that actually wOREks for us.",
    authors = ["Nickster258", "PaukkuPalikka", "StackDoubleFlow", "sodiboo"],
    dependencies = [Dependency(id = "luckperms")]
)
class ChattORE @Inject constructor(val proxy: ProxyServer, val logger: Logger, @DataDirectory dataFolder: Path) {
    lateinit var luckPerms: LuckPerms
    lateinit var config: Config
    lateinit var database: Storage
    var discordNetwork: DiscordApi? = null
    private val replyMap: MutableMap<UUID, UUID> = hashMapOf()
    private var discordMap: Map<String, DiscordApi> = hashMapOf()
    private var fileTypeMap: Map<String, List<String>> = hashMapOf()
    private var emojis: Map<String, String> = hashMapOf()
    private var emojisToNames: Map<String, String> = hashMapOf()
    private val dataFolder = dataFolder.toFile()
    private val uuidRegex = """[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""".toRegex()
    private var chatReplacements: MutableList<TextReplacementConfig> = mutableListOf(
        formatReplacement("**", "b"),
        formatReplacement("*", "i"),
        formatReplacement("__", "u"),
        formatReplacement("~~", "st")
    )

    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        config = loadConfig()
        luckPerms = LuckPermsProvider.get()
        database = Storage(this.dataFolder.resolve(config[ChattORESpec.storage]).toString())
        this.javaClass.getResourceAsStream("/emojis.csv")?.let { inputStream ->
            emojis = inputStream.reader().readLines().associate { item ->
                val parts = item.split(",")
                parts[0] to parts[1]
            }
            emojisToNames = emojis.entries.associateBy({ it.value }) { it.key }
            chatReplacements.add(buildEmojiReplacement(emojis))
            logger.info("Loaded ${emojis.size} emojis")
        }
        this.javaClass.getResourceAsStream("/filetypes.json")?.let { inputStream ->
            val jsonElement = Json.parseToJsonElement(inputStream.reader().readText())
            fileTypeMap = jsonElement.jsonObject.mapValues { (_, value) ->
                value.jsonArray.map { it.jsonPrimitive.content }
            }
            fileTypeMap.forEach { (key, values) ->
                logger.info("Loaded ${values.size} of type $key")
            }
        }
        if (config[ChattORESpec.discord.enable]) {
            discordNetwork = DiscordApiBuilder()
                .setToken(config[ChattORESpec.discord.networkToken])
                .setAllIntents()
                .login()
                .join()
            discordMap = loadDiscordTokens()
            discordMap.forEach { (_, discordApi) -> discordApi.updateActivity(config[ChattORESpec.discord.playingMessage]) }
            discordMap.values.firstOrNull()?.getChannelById(config[ChattORESpec.discord.channelId])?.ifPresent { channel ->
                channel.asTextChannel().ifPresent { textChannel ->
                    textChannel.addMessageCreateListener(DiscordListener(this, emojisToNames))
                }
            }
        }
        this.database.updateLocalUsernameCache()
        VelocityCommandManager(proxy, this).apply {
            registerCommand(Chattore(this@ChattORE))
            registerCommand(Emoji(this@ChattORE, emojis))
            registerCommand(HelpOp(this@ChattORE))
            registerCommand(Mail(this@ChattORE))
            registerCommand(Me(config, this@ChattORE))
            registerCommand(Message(config, this@ChattORE, replyMap))
            registerCommand(Nick(this@ChattORE))
            registerCommand(Profile(this@ChattORE))
            registerCommand(Reply(config, this@ChattORE, replyMap))
            setDefaultExceptionHandler(::handleCommandException, false)
            commandCompletions.registerCompletion("bool") { listOf("true", "false")}
            commandCompletions.registerCompletion("colors") { ctx ->
                (hexColorMap.values.map { it.second }
                    + if (ctx.input.isEmpty()) {
                    listOf("#", "&")
                } else if (ctx.input.startsWith("#")) {
                    (hexColorMap.values.map { it.first }
                        + ctx.input.padEnd(7, '0').let {
                        // do not duplicate if it's already in the list
                        // do not suggest if it's not a valid hex color
                        if (it.matches(Regex("^#[0-9A-Fa-f]{6}$")) && ctx.input.uppercase() !in hexColorMap.values.map { (hex, _) -> hex.substring(0, ctx.input.length) }) {
                            listOf(it.uppercase())
                        } else {
                            listOf()
                        }
                    })
                } else if (ctx.input.startsWith("&")) {
                    hexColorMap.keys.map { "&$it" }
                } else {
                    listOf()
                })
            }
            commandCompletions.registerCompletion("emojis") { emojis.keys }
            commandCompletions.registerCompletion("usernameCache") { database.uuidToUsernameCache.values }
            commandCompletions.registerCompletion("username") { listOf(it.player.username) }
            commandCompletions.registerCompletion("uuidAndUsernameCache") {
                database.uuidToUsernameCache.values + database.uuidToUsernameCache.keys.map { it.toString() }
            }
            commandCompletions.registerCompletion("nickPresets") { config[ChattORESpec.nicknamePresets].keys }
        }
        proxy.eventManager.register(this, ChatListener(this))
    }

    fun parsePlayerProfile(user: User, ign: String): Component {
        var group = user.primaryGroup
        this.luckPerms.groupManager.getGroup(user.primaryGroup)?.let {
            it.cachedData.metaData.prefix?.let { prefix -> group = prefix }
        }
        return config[ChattORESpec.format.playerProfile].render(
            mapOf(
                "about" to (this.database.getAbout(user.uniqueId) ?: "no about yet :(").toComponent(),
                "ign" to ign.toComponent(),
                "nickname" to (this.database.getNickname(user.uniqueId) ?: "No nickname set")
                    .render(mapOf(
                        "username" to ign.toComponent(),
                    )),
                "rank" to group.legacyDeserialize(),
            )
        )
    }

    fun getUsernameAndUuid(input: String): Pair<String, UUID> {
        var ign = input // Assume target is the IGN
        val uuid: UUID
        if (this.database.usernameToUuidCache.containsKey(ign)) {
            uuid = this.database.usernameToUuidCache.getValue(ign)
        } else {
            if (!uuidRegex.matches(input)) {
                throw ChattoreException("Invalid target specified")
            }
            uuid = UUID.fromString(input)
            val fetchedName = this.database.uuidToUsernameCache[uuid]
                ?: throw ChattoreException("We do not recognize that user!")
            ign = fetchedName
        }
        return Pair(ign, uuid)
    }

    fun fetchUuid(input: String): UUID? =
        if (this.database.usernameToUuidCache.containsKey(input)) {
            this.database.usernameToUuidCache.getValue(input)
        } else if (uuidRegex.matches(input)) {
            UUID.fromString(input)
        } else {
            null
        }

    private fun loadDiscordTokens(): Map<String, DiscordApi> {
        val availableServers = proxy.allServers.map { it.serverInfo.name.lowercase() }.sorted()
        val configServers = config[ChattORESpec.discord.serverTokens].map { it.key.lowercase() }.sorted()
        if (availableServers != configServers) {
            logger.warn(
                """
                    Supplied server keys in Discord configuration section does not match available servers:
                    Available servers: ${availableServers.joinToString()}
                    Configured servers: ${configServers.joinToString()}
                """.trimIndent()
            )
        }
        return config[ChattORESpec.discord.serverTokens].mapValues { (_, token) ->
            DiscordApiBuilder()
                .setToken(token)
                .setAllIntents()
                .login()
                .join()
        }
    }

    private fun loadConfig(reloaded: Boolean = false): Config {
        if (!dataFolder.exists()) {
            logger.info("No resource directory found, creating directory")
            dataFolder.mkdir()
        }
        val configFile = File(dataFolder, "config.yml")
        val loadedConfig = if (!configFile.exists()) {
            logger.info("No config file found, generating from default config.yml")
            configFile.createNewFile()
            Config { addSpec(ChattORESpec) }
        } else {
            Config { addSpec(ChattORESpec) }.from.yaml.watchFile(configFile)
        }
        loadedConfig.toYaml.toFile(configFile)
        logger.info("${if (reloaded) "Rel" else "L"}oaded config.yml")
        return loadedConfig
    }

    fun sendMessage(
        replyMap: MutableMap<UUID, UUID>,
        config: Config,
        player: Player,
        targetPlayer: Player,
        args: Array<String>
    ) {
        val statement = args.joinToString(" ")
        logger.info("${player.username} (${player.uniqueId}) -> " +
            "${targetPlayer.username} (${targetPlayer.uniqueId}): $statement")
        player.sendMessage(
            config[ChattORESpec.format.messageSent].render(
                mapOf(
                    "message" to prepareChatMessage(statement, player),
                    "recipient" to targetPlayer.username.toComponent()
                )
            )
        )
        targetPlayer.sendMessage(
            config[ChattORESpec.format.messageReceived].render(
                mapOf(
                    "message" to prepareChatMessage(statement, player),
                    "sender" to player.username.toComponent()
                )
            )
        )
        replyMap[targetPlayer.uniqueId] = player.uniqueId
        replyMap[player.uniqueId] = targetPlayer.uniqueId
    }

    private fun prepareChatMessage(message: String, player: Player?): Component {
        fun String.replaceObfuscate(canObfuscate: Boolean): String =
            if (canObfuscate) {
                this
            } else {
                this.replace("&k", "")
            }
        val canObfuscate = player?.hasPermission("chattore.chat.obfuscate") ?: false
        val urlRegex = """<?((http|https)://([\w_-]+(?:\.[\w_-]+)+)([^\s'<>]+)?)>?""".toRegex()
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
                val contentType = fileTypeMap.entries.find { type in it.value }?.key
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
        return builder.build().performReplacements(chatReplacements)
    }

    fun broadcast(component: Component) {
        proxy.allPlayers.forEach { it.sendMessage(component) }
    }

    fun broadcastPlayerConnection(message: String) {
        val discord = discordNetwork ?: return
        discord.getTextChannelById(config[ChattORESpec.discord.channelId])?.ifPresent {
            it.sendMessage(message)
        }
    }

    fun broadcastChatMessage(originServer: String, user: UUID, message: String) {
        val userManager = luckPerms.userManager
        val luckUser = userManager.getUser(user) ?: return
        val name = this.database.getNickname(user) ?: this.proxy.getPlayer(user).get().username
        val player = this.proxy.getPlayer(user).get()
        val sender = name.render(mapOf(
            "username" to player.username.toComponent()
        )).let {
            "<click:run_command:'/playerprofile info ${player.username}'><message></click>".render(it)
        };

        val prefix = luckUser.cachedData.metaData.prefix
            ?: luckUser.primaryGroup.replaceFirstChar(Char::uppercaseChar)

        broadcast(
            config[ChattORESpec.format.global].render(
                mapOf(
                    "message" to prepareChatMessage(message, player),
                    "sender" to sender,
                    "username" to Component.text(player.username),
                    "prefix" to prefix.legacyDeserialize(),
                )
            )
        )

        val discordApi = discordMap[originServer] ?: return
        val channel = discordApi.getTextChannelById(config[ChattORESpec.discord.channelId]).orElse(null) ?: run {
            logger.error("Could not get specified discord channel")
            return
        }

        val plainPrefix = PlainTextComponentSerializer.plainText().serialize(prefix.componentize())
        val content = config[ChattORESpec.discord.format]
            .replace("%prefix%", plainPrefix)
            .replace("%sender%", this.proxy.getPlayer(user).get().username.discordEscape())
            .replace("%message%", message)
        val discordMessage = MessageBuilder().setContent(content)
        discordMessage.send(channel)
    }

    fun broadcastDiscordMessage(sender: String, message: String) {
        val urlMarkdownRegex = """\[([^]]*)\]\(\s?(\S+)\s?\)""".toRegex()
        val transformedMessage = message.replace(urlMarkdownRegex) { matchResult ->
            val text = matchResult.groupValues[1].trim()
            val url = matchResult.groupValues[2].trim()
            "$text: $url"
        }.replace("""\s+""".toRegex(), " ")
        broadcast(
            config[ChattORESpec.format.discord].render(
                mapOf(
                    "sender" to sender.toComponent(),
                    "message" to prepareChatMessage(transformedMessage, null)
                )
            )
        )
    }

    fun sendPrivileged(component: Component, exclude: UUID = UUID.randomUUID()) {
        val privileged = proxy.allPlayers.filter {
            it.hasPermission("chattore.privileged")
                    && (it.uniqueId != exclude)
        }
        for (user in privileged) {
            user.sendMessage(component)
        }
    }

    private fun handleCommandException(
        command: BaseCommand,
        registeredCommand: RegisteredCommand<*>,
        sender: CommandIssuer,
        args: List<String>,
        throwable: Throwable
    ): Boolean {
        val exception = throwable as? ChattoreException ?: return false
        val message = exception.message ?: "Something went wrong!"
        if (sender is Player) {
            sender.sendMessage(config[ChattORESpec.format.error].render(message))
        } else {
            sender.sendMessage("Error: $message")
        }
        return true
    }

    fun getVersion(): String {
        return VERSION
    }

    fun reload() {
        // TODO
    }
}

class ChattoreException : Exception {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
