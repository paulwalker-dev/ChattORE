package chattore.commands

import chattore.*
import chattore.entity.ChattORESpec
import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Single
import co.aikar.commands.annotation.Subcommand
import com.velocitypowered.api.proxy.Player

@CommandAlias("profile|playerprofile")
@CommandPermission("chattore.profile")
class Profile(
    private val chattORE: ChattORE
) : BaseCommand() {

    @Subcommand("info")
    @CommandCompletion("@uuidAndUsernameCache")
    fun profile(player: Player, @Single target: String) {
        val usernameAndUuid = chattORE.getUsernameAndUuid(target)
        chattORE.luckPerms.userManager.loadUser(usernameAndUuid.second).whenComplete { user, throwable ->
            player.sendMessage(chattORE.parsePlayerProfile(user, usernameAndUuid.first))
        }
    }

    @Subcommand("about")
    @CommandPermission("chattore.profile.about")
    fun about(player: Player, about: String) {
        chattORE.database.setAbout(player.uniqueId, about)
        val response = chattORE.config[ChattORESpec.format.chattore].render(
            "Set your about to '$about'.".toComponent()
        )
        player.sendMessage(response)
    }

    @Subcommand("setabout")
    @CommandPermission("chattore.profile.about.others")
    @CommandCompletion("@uuidAndUsernameCache")
    fun setAbout(player: Player, @Single target: String, about: String) {
        val usernameAndUuid = chattORE.getUsernameAndUuid(target)
        chattORE.database.setAbout(usernameAndUuid.second, about)
        val response = chattORE.config[ChattORESpec.format.chattore].render(
            "Set about for '${usernameAndUuid.first}' to '$about'.".toComponent()
        )
        player.sendMessage(response)
        chattORE.proxy.getPlayer(usernameAndUuid.second).ifPresent {
            it.sendMessage(
                chattORE.config[ChattORESpec.format.chattore].render(
                    "Your about has been set to '$about'".toComponent()
                )
            )
        }
    }
}