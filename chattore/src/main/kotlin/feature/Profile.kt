package chattore.feature

import chattore.*
import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.Component
import net.luckperms.api.LuckPerms
import net.luckperms.api.model.user.User
import java.util.*

data class ProfileConfig(
    val profile: String = "<gold><st>  </st> Player Profile <st>  </st></gold><newline>IGN: <ign><newline>Nickname: <nickname><newline>Rank: <rank><newline><gold><st>                        </st></gold><newline>About me: <yellow><about><reset><newline><gold><st>                        </st></gold>",
    val format: String = "<gold>[</gold><red>ChattORE</red><gold>]</gold> <red><message></red>"
)

fun createProfileFeature(
    proxy: ProxyServer,
    database: Storage,
    luckPerm: LuckPerms,
    config: ProfileConfig
): Feature {
    return Feature(
        commands = listOf(Profile(proxy, database, luckPerm, config))
    )
}

@CommandAlias("profile|playerprofile")
@CommandPermission("chattore.profile")
class Profile(
    private val proxy: ProxyServer,
    private val database: Storage,
    private val luckPerms: LuckPerms,
    private val config: ProfileConfig
) : BaseCommand() {

    @Subcommand("info")
    @CommandCompletion("@uuidAndUsernameCache")
    fun profile(player: Player, @Single target: String) {
        val usernameAndUuid = getUsernameAndUuid(target)
        luckPerms.userManager.loadUser(usernameAndUuid.second).whenComplete { user, throwable ->
            player.sendMessage(parsePlayerProfile(user, usernameAndUuid.first))
        }
    }

    @Subcommand("about")
    @CommandPermission("chattore.profile.about")
    fun about(player: Player, about: String) {
        database.setAbout(player.uniqueId, about)
        val response = config.format.render(
            "Set your about to '$about'.".toComponent()
        )
        player.sendMessage(response)
    }

    @Subcommand("setabout")
    @CommandPermission("chattore.profile.about.others")
    @CommandCompletion("@uuidAndUsernameCache")
    fun setAbout(player: Player, @Single target: String, about: String) {
        val usernameAndUuid = getUsernameAndUuid(target)
        database.setAbout(usernameAndUuid.second, about)
        val response = config.format.render(
            "Set about for '${usernameAndUuid.first}' to '$about'.".toComponent()
        )
        player.sendMessage(response)
        proxy.getPlayer(usernameAndUuid.second).ifPresent {
            it.sendMessage(
                config.format.render(
                    "Your about has been set to '$about'".toComponent()
                )
            )
        }
    }

    fun parsePlayerProfile(user: User, ign: String): Component {
        var group = user.primaryGroup
        luckPerms.groupManager.getGroup(user.primaryGroup)?.let {
            it.cachedData.metaData.prefix?.let { prefix -> group = prefix }
        }
        return config.profile.render(
            mapOf(
                "about" to (database.getAbout(user.uniqueId) ?: "no about yet :(").toComponent(),
                "ign" to ign.toComponent(),
                "nickname" to (database.getNickname(user.uniqueId) ?: "No nickname set")
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
        if (database.usernameToUuidCache.containsKey(ign)) {
            uuid = database.usernameToUuidCache.getValue(ign)
        } else {
            if (!uuidRegex.matches(input)) {
                throw ChattoreException("Invalid target specified")
            }
            uuid = UUID.fromString(input)
            val fetchedName = database.uuidToUsernameCache[uuid]
                ?: throw ChattoreException("We do not recognize that user!")
            ign = fetchedName
        }
        return Pair(ign, uuid)
    }
}
