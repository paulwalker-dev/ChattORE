package org.openredstone.chattore.feature

import org.openredstone.chattore.*
import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.Component
import net.luckperms.api.LuckPerms
import net.luckperms.api.model.user.User as LPUser

data class ProfileConfig(
    val profile: String = "<gold><st>  </st> Player Profile <st>  </st></gold><newline>IGN: <ign><newline>Nickname: <nickname><newline>Rank: <rank><newline><gold><st>                        </st></gold><newline>About me: <yellow><about><reset><newline><gold><st>                        </st></gold>"
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
    fun profile(player: Player, target: KnownUser) {
        // TODO this might fail
        luckPerms.userManager.loadUser(target.uuid).whenComplete { user, _ ->
            player.sendMessage(parsePlayerProfile(user, target.name))
        }
    }

    @Subcommand("about")
    @CommandPermission("chattore.profile.about")
    fun about(player: Player, about: String) {
        database.setAbout(player.uniqueId, about)
        player.sendInfo("Set your about to '$about'.")
    }

    @Subcommand("setabout")
    @CommandPermission("chattore.profile.about.others")
    // TODO do we want to complete uuids too here?
    @CommandCompletion("@uuidAndUsernameCache")
    fun setAbout(player: Player, target: User, about: String) {
        database.setAbout(target.uuid, about)
        player.sendInfo("Set about for '${userCache.usernameOrUuid(target)}' to '$about'.")
        proxy.playerOrNull(target.uuid)?.sendInfo("Your about has been set to '$about'")
    }

    private fun parsePlayerProfile(user: LPUser, ign: String): Component {
        var group = user.primaryGroup
        luckPerms.groupManager.getGroup(user.primaryGroup)?.let {
            it.cachedData.metaData.prefix?.let { prefix -> group = prefix }
        }
        return config.profile.render(
            "about" toS (database.getAbout(user.uniqueId) ?: "no about yet :("),
            "ign" toS ign,
            "nickname" toC (database.getNickname(user.uniqueId)?.render(ign) ?: "No nickname set".toComponent()),
            "rank" toC group.legacyDeserialize(),
        )
    }
}
