package org.openredstone.chattore.feature

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import com.velocitypowered.api.proxy.Player
import org.openredstone.chattore.*

fun createChattoreFeature(
    plugin: ChattORE,
): Feature {
    return Feature(
        commands = listOf(Chattore(plugin))
    )
}

@CommandAlias("chattore")
class Chattore(
    private val plugin: ChattORE,
) : BaseCommand() {

    @Default
    @CatchUnknown
    @Subcommand("version")
    fun version(player: Player) {
        player.sendInfoMM("Version <gray>$VERSION")
    }

    @Subcommand("reload")
    @CommandPermission("chattore.manage")
    fun reload(player: Player) {
        plugin.reload()
        player.sendInfo("Reloaded ChattORE")
    }
}
