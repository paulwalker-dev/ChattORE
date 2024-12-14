package chattore

import chattore.commands.MailboxItem
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

object About : Table("about") {
    val uuid = varchar("about_uuid", 36).uniqueIndex()
    val about = varchar("about_about", 512)
    override val primaryKey = PrimaryKey(uuid)
}

object Mail : Table("mail") {
    val id = integer("mail_id").autoIncrement()
    val timestamp = integer("mail_timestamp")
    val sender = varchar("mail_sender", 36).index()
    val recipient = varchar("mail_recipient", 36).index()
    val read = bool("mail_read").default(false)
    val message = varchar("mail_message", 512)
    override val primaryKey = PrimaryKey(id)
}

object Nick : Table("nick") {
    val uuid = varchar("nick_uuid", 36).uniqueIndex()
    val nick = varchar("nick_nick", 2048)
    override val primaryKey = PrimaryKey(uuid)
}

object UsernameCache : Table("username_cache") {
    val uuid = varchar("cache_user", 36).uniqueIndex()
    val username = varchar("cache_username", 16).index()
    override val primaryKey = PrimaryKey(uuid)
}

object BoolSetting : Table("setting_bool") {
    val uuid = varchar("setting_uuid", 36).index()
    val key = varchar("setting_key", 32).index()
    val value = bool("setting_value")
    val uuidKeyIndex = index("setting_bool_uuid_key_index", true, uuid, key)
}

object StringSetting : Table("setting_string") {
    val uuid = varchar("setting_uuid", 36).index()
    val key = varchar("setting_key", 32).index()
    val value = varchar("setting_value", 512)
    val uuidKeyIndex = index("setting_string_uuid_key_index", true, BoolSetting.uuid, BoolSetting.key)
}

object IntSetting : Table("setting_int") {
    val uuid = varchar("setting_uuid", 36).index()
    val key = varchar("setting_key", 32).index()
    val value = integer("setting_value")
    val uuidKeyIndex = index("setting_int_uuid_key_index", true, BoolSetting.uuid, BoolSetting.key)
}

open class Setting<T>(val key: String)

object SpyEnabled : Setting<Boolean>("spy")

class Storage(
    dbFile: String
) {
    var uuidToUsernameCache = mapOf<UUID, String>()
    var usernameToUuidCache = mapOf<String, UUID>()
    val database = Database.connect("jdbc:sqlite:${dbFile}", "org.sqlite.JDBC")

    init {
        initTables()
    }

    private fun initTables() = transaction(database) {
        SchemaUtils.create(
            About, Mail, Nick, UsernameCache,
            BoolSetting, StringSetting, IntSetting)
    }

    fun setAbout(uuid: UUID, about: String) = transaction(database) {
        About.upsert {
            it[this.uuid] = uuid.toString()
            it[this.about] = about
        }
    }

    fun getAbout(uuid: UUID) : String? = transaction(database) {
        About.selectAll().where { About.uuid eq uuid.toString() }.firstOrNull()?.let { it[About.about] }
    }

    fun removeNickname(target: UUID) = transaction(database) {
        Nick.deleteWhere { Nick.uuid eq target.toString() }
    }

    fun getNickname(target: UUID): String? = transaction(database) {
        Nick.selectAll().where { Nick.uuid eq target.toString() }.firstOrNull()?.let { it[Nick.nick] }
    }

    fun setNickname(target: UUID, nickname: String) = transaction(database) {
        Nick.upsert {
            it[this.uuid] = target.toString()
            it[this.nick] = nickname
        }
    }

    fun ensureCachedUsername(user: UUID, username: String) = transaction(database) {
        UsernameCache.upsert {
            it[this.uuid] = user.toString()
            it[this.username] = username
        }
        updateLocalUsernameCache()
    }

    fun updateLocalUsernameCache() {
        uuidToUsernameCache = transaction(database) {
            UsernameCache.selectAll().associate {
                UUID.fromString(it[UsernameCache.uuid]) to it[UsernameCache.username]
            }
        }
        usernameToUuidCache = uuidToUsernameCache.entries.associate{(k,v)-> v to k}
    }

    fun insertMessage(sender: UUID, recipient: UUID, message: String) = transaction(database) {
        Mail.insert {
            it[this.timestamp] = System.currentTimeMillis().floorDiv(1000).toInt()
            it[this.sender] = sender.toString()
            it[this.recipient] = recipient.toString()
            it[this.message] = message
        }
    }

    fun readMessage(recipient: UUID, id: Int): Pair<UUID, String>? = transaction(database) {
        Mail.selectAll().where { (Mail.id eq id) and (Mail.recipient eq recipient.toString()) }
            .firstOrNull()?.let { toReturn ->
                markRead(id, true)
                UUID.fromString(toReturn[Mail.sender]) to toReturn[Mail.message]
            }
    }

    fun getMessages(recipient: UUID): List<MailboxItem> = transaction(database) {
        Mail.selectAll().where { Mail.recipient eq recipient.toString() }
            .orderBy(Mail.timestamp to SortOrder.DESC) .map {
            MailboxItem(
                it[Mail.id],
                it[Mail.timestamp],
                UUID.fromString(it[Mail.sender]),
                it[Mail.read]
            )
        }
    }

    private fun markRead(id: Int, read: Boolean) = transaction(database) {
        Mail.update({Mail.id eq id}) {
            it[this.read] = read
        }
    }

    inline fun <reified T> setSetting(setting: Setting<T>, uuid: UUID, value: T) = transaction(database) {
        when (T::class) {
            Boolean::class -> BoolSetting.upsert {
                it[BoolSetting.uuid] = uuid.toString()
                it[BoolSetting.key] = setting.key
                it[BoolSetting.value] = value as Boolean
            }
            String::class -> StringSetting.upsert {
                it[StringSetting.uuid] = uuid.toString()
                it[StringSetting.key] = setting.key
                it[StringSetting.value] = value as String
            }
            Int::class -> IntSetting.upsert {
                it[IntSetting.uuid] = uuid.toString()
                it[IntSetting.key] = setting.key
                it[IntSetting.value] = value as Int
            }
            else -> throw IllegalArgumentException("Unsupported type for setting: ${T::class.simpleName}")
        }
    }

    inline fun <reified T> getSetting(setting: Setting<T>, uuid: UUID): T? = transaction {
        return@transaction when (T::class) {
            Boolean::class -> {
                val result = BoolSetting.selectAll().where {
                    (BoolSetting.uuid eq uuid.toString()) and (BoolSetting.key eq setting.key)
                }.singleOrNull() ?: return@transaction null
                result[BoolSetting.value] as? T
            }
            String::class -> {
                val result = StringSetting.selectAll().where {
                    (StringSetting.uuid eq uuid.toString()) and (StringSetting.key eq setting.key)
                }.singleOrNull() ?: return@transaction null
                result[StringSetting.value] as? T
            }
            Int::class -> {
                val result = IntSetting.selectAll().where {
                    (IntSetting.uuid eq uuid.toString()) and (IntSetting.key eq setting.key)
                }.singleOrNull() ?: return@transaction null
                result[IntSetting.value] as? T
            }
            else -> throw IllegalArgumentException("Unsupported type for setting: ${T::class.simpleName}")
        }
    }
}