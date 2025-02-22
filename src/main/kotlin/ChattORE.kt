package chattore

import chattore.entity.ChattOREConfig
import chattore.feature.*
import co.aikar.commands.BaseCommand
import co.aikar.commands.CommandIssuer
import co.aikar.commands.InvalidCommandArgument
import co.aikar.commands.RegisteredCommand
import co.aikar.commands.VelocityCommandManager
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Dependency
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.TextReplacementConfig
import net.luckperms.api.LuckPerms
import net.luckperms.api.LuckPermsProvider
import org.slf4j.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

data class Feature(
    val commands: List<BaseCommand> = emptyList(),
    val listeners: List<Any> = emptyList(),
    val completions: List<Any> = emptyList(), // Could be useful, but currently not used
    val unload: () -> Unit = {},
)

const val VERSION = "1.2"

@Plugin(
    id = "chattore",
    name = "ChattORE",
    version = VERSION,
    url = "https://openredstone.org",
    description = "Because we want to have a chat system that actually wOREks for us.",
    authors = ["Nickster258", "PaukkuPalikka", "StackDoubleFlow", "sodiboo", "Waffle [Wueffi]"],
    dependencies = [Dependency(id = "luckperms")]
)
class ChattORE @Inject constructor(val proxy: ProxyServer, val logger: Logger, @DataDirectory dataFolder: Path) {
    lateinit var luckPerms: LuckPerms
    lateinit var config: ChattOREConfig
    lateinit var database: Storage
    lateinit var messenger: Messenger
    private lateinit var emojis: Map<String, String>
    lateinit var emojisToNames: Map<String, String>
    private val dataFolder = dataFolder.toFile()
    val chatReplacements: MutableList<TextReplacementConfig> = mutableListOf(
        formatReplacement("**", "b"),
        formatReplacement("*", "i"),
        formatReplacement("__", "u"),
        formatReplacement("~~", "st")
    )

    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        config = loadConfig()
        luckPerms = LuckPermsProvider.get()
        database = Storage(this.dataFolder.resolve(config.storage).toString())
        val pluginEvents = PluginEvents(this, proxy.eventManager)
        val userCache = UserCache.create(database.database, pluginEvents)
        emojis = loadResource("/emojis.csv").lineSequence().associate { item ->
            val parts = item.split(",")
            parts[0] to parts[1]
        }
        emojisToNames = emojis.entries.associateBy({ it.value }) { it.key }
        chatReplacements.add(buildEmojiReplacement(emojis))
        logger.info("Loaded ${emojis.size} emojis")

        messenger = Messenger(this, config.format.global)

        // command manager lol
        val commandManager = VelocityCommandManager(proxy, this).apply {
            setDefaultExceptionHandler(::handleCommandException, false)
            // TODO move to the place
            commandContexts.registerContext(User::class.java) {
                userCache.fetchUuid(it.popFirstArg())?.let(::User)
                    ?: throw InvalidCommandArgument("Don't know that user")
            }
            commandContexts.registerContext(KnownUser::class.java) {
                val uuid = userCache.fetchUuid(it.popFirstArg()) ?: throw InvalidCommandArgument("Don't know that user")
                val ign = userCache.usernameOrNull(uuid) ?: throw InvalidCommandArgument("Don't know that user")
                KnownUser(uuid, ign)
            }
            commandCompletions.registerCompletion("usernameCache") { userCache.usernames }
            commandCompletions.registerCompletion("uuidAndUsernameCache") {
                userCache.usernames + userCache.uuids.map(UUID::toString)
            }
            // TODO check this
            commandCompletions.setDefaultCompletion("usernameCache", User::class.java, KnownUser::class.java)

            commandCompletions.registerCompletion("bool") { listOf("true", "false") }
            commandCompletions.registerCompletion("colors") { ctx ->
                (hexColorMap.values.map { it.second }
                    + if (ctx.input.isEmpty()) {
                    listOf("#", "&")
                } else if (ctx.input.startsWith("#")) {
                    (hexColorMap.values.map { it.first }
                        + ctx.input.padEnd(7, '0').let {
                        // do not duplicate if it's already in the list
                        // do not suggest if it's not a valid hex color
                        if (it.matches(Regex("^#[0-9A-Fa-f]{6}$"))
                            && ctx.input.uppercase() !in hexColorMap.values.map
                            { (hex, _) -> hex.substring(0, ctx.input.length) }
                        ) {
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
            commandCompletions.registerCompletion("username") { listOf(it.player.username) }
            commandCompletions.registerCompletion("nickPresets") { config.nicknamePresets.keys }
        }
        val features = listOf(
            createChatFeature(
                logger, messenger, ChatConfirmationConfig(
                    config.regexes,
                    config.format.chatConfirmPrompt,
                    config.format.chatConfirm,
                )
            ),
            createChattoreFeature(this),
            createDiscordFeature(
                this, DiscordConfig(
                    config.discord.enable,
                    config.discord.networkToken,
                    config.discord.channelId,
                    config.discord.chadId,
                    config.discord.playingMessage,
                    config.discord.format,
                    config.discord.serverTokens,
                    config.format.discord,
                )
            ),
            createEmojiFeature(emojis),
            createFunCommandsFeature(logger, messenger, proxy.commandManager),
            createHelpOpFeature(
                logger, messenger, HelpOpConfig(
                    config.format.help,
                )
            ),
            createJoinLeaveFeature(
                messenger, proxy.eventManager, JoinLeaveConfig(
                    config.format.join,
                    config.format.leave,
                    config.format.joinDiscord,
                    config.format.leaveDiscord,
                )
            ),
            createMailFeature(
                this, userCache, MailConfig(
                    config.format.mailReceived,
                    config.format.mailSent,
                    config.format.mailUnread,
                )
            ),
            createMessageFeature(
                proxy, logger, messenger, MessageConfig(
                    config.format.messageReceived,
                    config.format.messageSent,
                )
            ),
            createNicknameFeature(
                proxy, database, userCache, NicknameConfig(
                    config.clearNicknameOnChange,
                    // IDK, this when config
                    config.nicknamePresets.mapValues { (_, v) -> NickPreset(v) }.toSortedMap(),
                )
            ),
            createProfileFeature(
                proxy, database, luckPerms, userCache, ProfileConfig(
                    config.format.playerProfile,
                )
            ),
            createSpyingFeature(
                database, messenger, SpyingConfig(
                    config.format.commandSpy,
                )
            )
        )
        features.forEach { (commands, listeners, completions) ->
            commands.forEach(commandManager::registerCommand)
            //completions.forEach { (it, handler) -> commandManager.commandCompletions.registerCompletion(it) { handler } }
            listeners.forEach { proxy.eventManager.register(this, it) }
        }
        logger.info("Loaded ${features.size} features")
    }

    private fun loadConfig(): ChattOREConfig {
        if (!dataFolder.exists()) {
            logger.info("No resource directory found, creating")
            dataFolder.mkdir()
        }
        val configFile = File(dataFolder, "config.yml")
        if (!configFile.exists()) {
            logger.info("No config file found, creating")
            // NOTE this is an empty YAML dictionary, it will get populated from default config
            Files.writeString(configFile.toPath(), "{}")
        }
        val config = readConfig<ChattOREConfig>(configFile)
        // save migrated config
        writeConfig(config, configFile)
        logger.info("Loaded config.yml")
        return config
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
            sender.sendSimpleS("<b><red>Oh NO ! </red></b><gray>:</gray> <red><message></red>", message)
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
