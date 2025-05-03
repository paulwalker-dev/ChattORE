import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import java.util.*

class ChattoreExpansion(private val afkUsers: MutableSet<UUID>) : PlaceholderExpansion() {

    private val badge = "[AFK]"

    override fun getIdentifier(): String = "chattore"

    override fun getAuthor(): String = "Open Redstone Engineers"

    override fun getVersion(): String = "1.0.0"

    override fun onRequest(player: OfflinePlayer, params: String): String? {
        return when (params) {
            "afk_badge" -> {
                if (player.uniqueId in afkUsers) {
                    return badge
                }
                return ""
            }
            else -> null
        }
    }

}
