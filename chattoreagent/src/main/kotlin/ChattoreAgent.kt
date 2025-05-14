import feature.startAfkFeature
import feature.startAliasFeature
import feature.startChatFeature
import org.bukkit.plugin.java.JavaPlugin

class ChattoreAgent : JavaPlugin() {
    override fun onEnable() {
        logger.info("Starting AFK feature")
        startAfkFeature(this)
        logger.info("Starting Chat feature")
        startChatFeature(this)
        logger.info("Starting Alias feature")
        startAliasFeature(this)
    }
}
