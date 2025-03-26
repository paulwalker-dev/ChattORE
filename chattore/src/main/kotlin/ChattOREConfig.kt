package org.openredstone.chattore

import java.util.*

data class ChattOREConfig(
    val storage: String = "storage.db",
    val clearNicknameOnChange: Boolean = true,
    val regexes: List<String> = emptyList(),

    val discord: ChattoreDiscordConfig = ChattoreDiscordConfig(),
    val format: ChattoreFormatConfig = ChattoreFormatConfig(),

    val nicknamePresets: SortedMap<String, String> = pridePresets,
)

data class ChattoreDiscordConfig(
    val enable: Boolean = false,
    val networkToken: String = "nouNetwork",
    val playingMessage: String = "on the ORE Network",
    val channelId: Long = 1234L,
    val serverTokens: Map<String, String> = mapOf(
        "serverOne" to "token1",
        "serverTwo" to "token2",
        "serverThree" to "token3"
    ),
    val format: String = "`%prefix%` **%sender%**: %message%",
    val chadId: Long = 1234L,
)

data class ChattoreFormatConfig(
    val global: String = "<prefix> <gray>|</gray> <hover:show_text:'<username> | <i>Click for more</i>'><yellow><sender></yellow></hover><gray>:</gray> <message>",
    val discord: String = "<dark_aqua>Discord</dark_aqua> <gray>|</gray> <dark_purple><sender></dark_purple><gray>:</gray> <message>",
    val mailReceived: String = "<gold>[</gold><red>From <sender></red><gold>]</gold> <message>",
    val mailSent: String = "<gold>[</gold><red>To <recipient></red><gold>]</gold> <message>",
    val mailUnread: String = "<yellow>You have <red><count></red> unread message(s)! <gold><b><hover:show_text:'View your mailbox'><click:run_command:'/mail mailbox'>Click here to view</click></hover></b></gold>.",
    val messageReceived: String = "<gold>[</gold><red><sender></red> <gold>-></gold> <red>me</red><gold>]</gold> <message>",
    val messageSent: String = "<gold>[</gold><red>me</red> <gold>-></gold> <red><recipient></red><gold>]</gold> <message>",
    // Other Roles: <alt_ranks><newline>
    val playerProfile: String = "<gold><st>  </st> Player Profile <st>  </st></gold><newline>IGN: <ign><newline>Nickname: <nickname><newline>Rank: <rank><newline><gold><st>                        </st></gold><newline>About me: <yellow><about><reset><newline><gold><st>                        </st></gold>",
    val socialSpy: String = "<gold>[</gold><sender> <gold>-></gold> <red><recipient></red><gold>]</gold> <message>",
    val commandSpy: String = "<gold><sender>: <message>",
    val chatConfirm: String = "<red>Override recognized",
    val chatConfirmPrompt: String = "<red><bold>The following message was not sent because it contained potentially inappropriate language:<newline><reset><message><newline><red>To send this message anyway, run <gray>/confirmmessage<red>.",
    val help: String = "<gold>[</gold><red>Help</red><gold>]</gold> <red><sender></red><gold>:</gold> <message>",
    val join: String = "<yellow><player> has joined the network",
    val leave: String = "<yellow><player> has left the network",
    val joinDiscord: String = "**<player> has joined the network**",
    val leaveDiscord: String = "**<player> has left the network**",
)
