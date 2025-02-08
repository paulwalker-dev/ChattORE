package chattore.commands

import chattore.*
import chattore.entity.ChattORESpec
import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.text.Component
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.*

fun getRelativeTimestamp(unixTimestamp: Long): String {
    val currentTime = LocalDateTime.now(ZoneOffset.UTC)
    val eventTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(unixTimestamp), ZoneOffset.UTC)

    val difference = ChronoUnit.MINUTES.between(eventTime, currentTime)

    return when {
        difference < 1 -> "just now"
        difference < 60 -> "$difference minutes ago"
        difference < 120 -> "an hour ago"
        difference < 1440 -> "${difference / 60} hours ago"
        else -> "${difference / 1440} days ago"
    }
}

data class MailboxItem(val id: Int, val timestamp: Int, val sender: UUID, val read: Boolean)

class MailContainer(private val uuidMapping: Map<UUID, String>, private val messages: List<MailboxItem>) {
    private val pageSize = 6
    fun getPage(page: Int = 0) : Component {
        val maxPage = messages.size / pageSize
        if (page > maxPage || page < 0) {
            return "Invalid page requested".toComponent()
        }
        val requestedMessages = messages.subList(page * pageSize, messages.size).take(pageSize)
        var body = ("<red>Mailbox, page $page</red><newline><gold>ID: Sender Timestamp").miniMessageDeserialize()
        requestedMessages.forEach {
            val mini = "<newline><yellow><hover:show_text:'<red>Click to read'><click:run_command:/mail read ${it.id}>" +
                "From: <gold><sender></gold>, <timestamp></click></hover> (<read>)</yellow>"
            val readComponent = if (!it.read) {
                "<b><red>Unread</red></b>".miniMessageDeserialize()
            } else {
                "<i><yellow>Read</yellow></i>".miniMessageDeserialize()
            }
            val item = mini.render(
                mapOf(
                    "sender" to uuidMapping.getValue(it.sender).toComponent(),
                    "timestamp" to getRelativeTimestamp(it.timestamp.toLong()).toComponent(),
                    "read" to readComponent
                )
            )
            body = body.append(item)
        }
        if (maxPage > 0) {
            var pageMini = "<newline>"
            pageMini += if (page == 0) {
                "<red><hover:show_text:'<red>No previous page'>\uD83D\uDEAB</hover></red>"
            } else {
                "<red><hover:show_text:'<red>Previous page'><click:run_command:/mailbox ${page-1}>←</click></hover></red>"
            }
            pageMini += " <yellow>|<yellow> "
            pageMini += if (page == maxPage) {
                "<red><hover:show_text:'<red>No next page'>\uD83D\uDEAB</hover></red>"
            } else {
                "<red><hover:show_text:'<red>Next page'><click:run_command:/mailbox ${page+1}>→</click></hover></red>"
            }
            body = body.append(pageMini.miniMessageDeserialize())
        }
        return body
    }
}

@CommandAlias("mail")
@Description("Send a message to an offline player")
@CommandPermission("chattore.mail")
class Mail(
    private val chattORE: ChattORE
) : BaseCommand() {

    private val mailTimeouts = mutableMapOf<UUID, Long>()

    @Default
    @CatchUnknown
    @CommandAlias("mailbox")
    @Subcommand("mailbox")
    fun mailbox(player: Player, @Default("0") page: Int) {
        val container = MailContainer(
            chattORE.database.uuidToUsernameCache,
            chattORE.database.getMessages(player.uniqueId)
        )
        player.sendMessage(container.getPage(page))
    }

    @Subcommand("send")
    @CommandCompletion("@usernameCache")
    fun send(player: Player, @Single target: String, message: String) {
        val now = System.currentTimeMillis().floorDiv(1000)
        mailTimeouts[player.uniqueId]?.let {
            // 60 second timeout to prevent flooding
            if (now < it + 60) throw ChattoreException("You are mailing too quickly!")
        }
        val targetUuid = chattORE.database.usernameToUuidCache[target]
            ?: throw ChattoreException("We do not recognize that user!")
        mailTimeouts[player.uniqueId] = now
        chattORE.database.insertMessage(player.uniqueId, targetUuid, message)
        val response = chattORE.config[ChattORESpec.format.mailSent].render(
            mapOf(
                "message" to message.toComponent(),
                "recipient" to target.toComponent()
            )
        )
        player.sendMessage(response)
    }

    @Subcommand("read")
    fun read(player: Player, id: Int) {
        chattORE.database.readMessage(player.uniqueId, id)?.let {
            val response = chattORE.config[ChattORESpec.format.mailReceived].render(
                mapOf(
                    "message" to it.second.toComponent(),
                    "sender" to chattORE.database.uuidToUsernameCache.getValue(it.first).toComponent()
                )
            )
            player.sendMessage(response)
        } ?: run {
            throw ChattoreException("Invalid message ID!")
        }
    }
}