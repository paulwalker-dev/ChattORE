package chattore.commands

import chattore.*
import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import com.velocitypowered.api.proxy.Player
import chattore.entity.ChattORESpec

@CommandAlias("confirmmessage")
@CommandPermission("chattore.confirmmessage")
class ConfirmMessage(
    private val chattORE: ChattORE
) : BaseCommand() {

    @Default
    fun default(player: Player) {
        val message = chattORE.chatConfirmMap[player.uniqueId] ?:
            throw ChattoreException("You have no message to confirm!")
        chattORE.logger.info("${player.username} (${player.uniqueId}) FLAGGED MESSAGE OVERRIDE: $message")
        player.sendMessage(chattORE.config[ChattORESpec.format.chatConfirm].miniMessageDeserialize())
        player.currentServer.ifPresent { server ->
            chattORE.broadcastChatMessage(server.serverInfo.name, player.uniqueId, message)
        }
        chattORE.chatConfirmMap.remove(player.uniqueId)
    }
}
