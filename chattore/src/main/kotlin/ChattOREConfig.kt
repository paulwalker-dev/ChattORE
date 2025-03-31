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
    val global: String = "<prefix> <gray>|</gray> <sender><gray>:</gray> <message>",
    val discord: String = "<dark_aqua>Discord</dark_aqua> <gray>|</gray> <dark_purple><sender></dark_purple><gray>:</gray> <message>",
    val join: String = "<yellow><player> has joined the network",
    val leave: String = "<yellow><player> has left the network",
    val joinDiscord: String = "**<player> has joined the network**",
    val leaveDiscord: String = "**<player> has left the network**",
)
