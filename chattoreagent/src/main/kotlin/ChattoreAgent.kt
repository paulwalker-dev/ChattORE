import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin

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

class ChattoreAgent : JavaPlugin() {
    override fun onEnable() {
        server.pluginManager.registerEvents(ChatListener(), this)
    }
}
