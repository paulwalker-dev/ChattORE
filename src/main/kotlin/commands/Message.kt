package chattore.commands

import chattore.*
import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import com.uchuhimo.konf.Config
import com.velocitypowered.api.proxy.Player
import java.util.*

@CommandAlias("m|msg|message|vmsg|vmessage|whisper|tell")
@CommandPermission("chattore.message")
class Message(
    private val config: Config,
    private val chattORE: ChattORE,
    private val replyMap: MutableMap<UUID, UUID>
) : BaseCommand() {

    @Default
    @Syntax("[target] <message>")
    @CommandCompletion("@players")
    fun default(player: Player, target: String, args: Array<String>) {
        chattORE.proxy.getPlayer(target).ifPresentOrElse({ targetPlayer ->
            chattORE.sendMessage(replyMap, config, player, targetPlayer, args)
        }, {
            throw ChattoreException("That user doesn't exist!")
        })
    }
}
