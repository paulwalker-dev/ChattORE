package chattore.feature

import chattore.ChattORE
import chattore.ChattoreException
import chattore.Feature
import chattore.legacyDeserialize
import chattore.render
import chattore.toComponent
import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.text.Component
import net.luckperms.api.model.user.User
import java.util.*

data class ProfileConfig(
    val profile: String = "<gold><st>  </st> Player Profile <st>  </st></gold><newline>IGN: <ign><newline>Nickname: <nickname><newline>Rank: <rank><newline><gold><st>                        </st></gold><newline>About me: <yellow><about><reset><newline><gold><st>                        </st></gold>",
    val format: String = "<gold>[</gold><red>ChattORE</red><gold>]</gold> <red><message></red>"
)

fun createProfileFeature(
    plugin: ChattORE,
    config: ProfileConfig
): Feature {
    return Feature(
        commands = listOf(Profile(plugin, config))
    )
}

@CommandAlias("profile|playerprofile")
@CommandPermission("chattore.profile")
class Profile(
    private val plugin: ChattORE,
    private val config: ProfileConfig
) : BaseCommand() {

    @Subcommand("info")
    @CommandCompletion("@uuidAndUsernameCache")
    fun profile(player: Player, @Single target: String) {
        val usernameAndUuid = getUsernameAndUuid(target)
        plugin.luckPerms.userManager.loadUser(usernameAndUuid.second).whenComplete { user, throwable ->
            player.sendMessage(parsePlayerProfile(user, usernameAndUuid.first))
        }
    }

    @Subcommand("about")
    @CommandPermission("chattore.profile.about")
    fun about(player: Player, about: String) {
        plugin.database.setAbout(player.uniqueId, about)
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
        plugin.database.setAbout(usernameAndUuid.second, about)
        val response = config.format.render(
            "Set about for '${usernameAndUuid.first}' to '$about'.".toComponent()
        )
        player.sendMessage(response)
        plugin.proxy.getPlayer(usernameAndUuid.second).ifPresent {
            it.sendMessage(
                config.format.render(
                    "Your about has been set to '$about'".toComponent()
                )
            )
        }
    }

    fun parsePlayerProfile(user: User, ign: String): Component {
        var group = user.primaryGroup
        plugin.luckPerms.groupManager.getGroup(user.primaryGroup)?.let {
            it.cachedData.metaData.prefix?.let { prefix -> group = prefix }
        }
        return config.profile.render(
            mapOf(
                "about" to (plugin.database.getAbout(user.uniqueId) ?: "no about yet :(").toComponent(),
                "ign" to ign.toComponent(),
                "nickname" to (plugin.database.getNickname(user.uniqueId) ?: "No nickname set")
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
        if (plugin.database.usernameToUuidCache.containsKey(ign)) {
            uuid = plugin.database.usernameToUuidCache.getValue(ign)
        } else {
            if (!plugin.uuidRegex.matches(input)) {
                throw ChattoreException("Invalid target specified")
            }
            uuid = UUID.fromString(input)
            val fetchedName = plugin.database.uuidToUsernameCache[uuid]
                ?: throw ChattoreException("We do not recognize that user!")
            ign = fetchedName
        }
        return Pair(ign, uuid)
    }
}
