import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.openredstone.chattore.common.ALIAS_CHANNEL
import org.openredstone.chattore.common.AliasMessage
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

fun getTime() = System.currentTimeMillis() / 1000

class ChatListener : Listener {
    @EventHandler
    fun onChatEvent(event: AsyncPlayerChatEvent) {
        event.isCancelled = true
    }

    @EventHandler
    fun onJoinEvent(event: PlayerJoinEvent) {
        event.joinMessage = null
    }

    @EventHandler
    fun onLeaveEvent(event: PlayerQuitEvent) {
        event.quitMessage = null
    }
}

class AfkListener(private val afkMap: ConcurrentHashMap<UUID, Long>) : Listener {
    @EventHandler
    fun onMoveEvent(event: PlayerMoveEvent) {
        afkMap[event.player.uniqueId] = getTime()
    }

    @EventHandler
    fun onQuitEvent(event: PlayerQuitEvent) {
        afkMap.remove(event.player.uniqueId)
    }

    @EventHandler
    fun onJoinEvent(event: PlayerJoinEvent) {
        afkMap[event.player.uniqueId] = getTime()
    }

    @EventHandler
    fun onChatEvent(event: AsyncPlayerChatEvent) {
        afkMap[event.player.uniqueId] = getTime()
    }
}

class ChattoreAgent : JavaPlugin() {
    private val afkMap: ConcurrentHashMap<UUID, Long> = ConcurrentHashMap()
    private val afkPlayers: MutableSet<UUID> = mutableSetOf()

    override fun onEnable() {
        startAfkTimer()
        logger.info("Registering events")
        server.pluginManager.registerEvents(ChatListener(), this)
        server.pluginManager.registerEvents(AfkListener(afkMap), this)
        logger.info("Registering message listener")
        server.messenger.registerIncomingPluginChannel(this, ALIAS_CHANNEL, this::onPluginMessageReceived)
        logger.info("Registering placeholders")
        server.pluginManager.getPlugin("PlaceholderAPI")?.let { ChattoreExpansion(afkPlayers).register() }
    }

    private fun startAfkTimer() {
        server.scheduler.scheduleSyncRepeatingTask(this, Runnable {
            afkMap.forEach { (uuid, lastActive) ->
                if (lastActive + 300 < getTime()) {
                    afkPlayers.add(uuid)
                } else {
                    afkPlayers.remove(uuid)
                }
            }
        }, 600L, 600L)
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun onPluginMessageReceived(channel: String, target: Player, data: ByteArray) {
        if (channel != ALIAS_CHANNEL) return
        val alias = Cbor.decodeFromByteArray<AliasMessage>(data)
        server.getPlayer(alias.targetPlayer)?.let { player ->
            server.dispatchCommand(player, alias.command)
        }
    }
}
