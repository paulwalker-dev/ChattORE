package chattore.commands

import chattore.ChattORE
import chattore.SpyEnabled
import chattore.render
import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import com.velocitypowered.api.proxy.Player
import chattore.entity.ChattORESpec
import chattore.legacyDeserialize

@CommandAlias("chattore")
class Chattore(
    private val chattORE: ChattORE
) : BaseCommand() {

    @Default
    @CatchUnknown
    @Subcommand("version")
    fun version(player: Player) {
        player.sendMessage(
            chattORE.config[ChattORESpec.format.chattore].render(
                "Version &7${chattORE.getVersion()}".legacyDeserialize()
            )
        )
    }

    @Subcommand("reload")
    @CommandPermission("chattore.manage")
    fun reload(player: Player) {
        chattORE.reload()
        player.sendMessage(
            chattORE.config[ChattORESpec.format.chattore].render(
                "Reloaded ChattORE"
            )
        )
    }

    @Subcommand("setting")
    inner class Setting : BaseCommand() {
        @Subcommand("spy")
        @CommandPermission("chattore.manage")
        fun spy(player: Player) {
            val setting = chattORE.database.getSetting(SpyEnabled, player.uniqueId)
            val newSetting = !(setting ?: false)
            chattORE.database.setSetting(SpyEnabled, player.uniqueId, newSetting)
            player.sendMessage(
                chattORE.config[ChattORESpec.format.chattore].render(
                    if (newSetting) {
                        "You are now spying on commands."
                    } else {
                        "You are no longer spying on commands."
                    }
                )
            )
        }
    }
}