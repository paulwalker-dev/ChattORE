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
    userCache: UserCache,
    config: ProfileConfig
): Feature {
    return Feature(
        commands = listOf(Profile(proxy, database, luckPerm, userCache, config))
    )
}

@CommandAlias("profile|playerprofile")
@CommandPermission("chattore.profile")
class Profile(
    private val proxy: ProxyServer,
    private val database: Storage,
    private val luckPerms: LuckPerms,
    private val userCache: UserCache,
    private val config: ProfileConfig
) : BaseCommand() {

    @Subcommand("info")
    @CommandCompletion("@uuidAndUsernameCache")
    fun profile(player: Player, @Single target: String) {
        val (username, uuid) = getUsernameAndUuid(target)
        // TODO this might fail
        luckPerms.userManager.loadUser(uuid).whenComplete { user, _ ->
            player.sendMessage(parsePlayerProfile(user, username))
        }
    }

    @Subcommand("about")
    @CommandPermission("chattore.profile.about")
    fun about(player: Player, about: String) {
        database.setAbout(player.uniqueId, about)
        player.sendSimpleS(
            config.format,
            "Set your about to '$about'.",
        )
    }

    @Subcommand("setabout")
    @CommandPermission("chattore.profile.about.others")
    @CommandCompletion("@uuidAndUsernameCache")
    fun setAbout(player: Player, @Single target: String, about: String) {
        val (username, uuid) = getUsernameAndUuid(target)
        database.setAbout(uuid, about)
        player.sendSimpleS(
            config.format,
            "Set about for '$username' to '$about'.",
        )
        proxy.getPlayer(uuid).ifPresent {
            it.sendSimpleS(
                config.format,
                "Your about has been set to '$about'",
            )
        }
    }

    private fun parsePlayerProfile(user: User, ign: String): Component {
        var group = user.primaryGroup
        luckPerms.groupManager.getGroup(user.primaryGroup)?.let {
            it.cachedData.metaData.prefix?.let { prefix -> group = prefix }
        }
        return config.profile.render(
            "about" toS (database.getAbout(user.uniqueId) ?: "no about yet :("),
            "ign" toS ign,
            "nickname" toC (database.getNickname(user.uniqueId) ?: "No nickname set")
                .render(
                    "username" toS ign,
                ),
            "rank" toC group.legacyDeserialize(),
        )
    }

    // TODO move to UserCache?
    private fun getUsernameAndUuid(input: String): Pair<String, UUID> {
        // next line conflates two error cases into what was previously one
        val uuid = userCache.fetchUuid(input) ?: throw ChattoreException("Invalid target specified")
        val ign = userCache.usernameOrNull(uuid) ?: throw ChattoreException("We do not recognize that user!")
        return ign to uuid
    }
}
