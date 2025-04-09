package org.openredstone.chattore

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.Logger
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.exists
import kotlin.io.path.writeText

private typealias ConfigVersion = Int

private val yaml = YAMLFactory()
    .configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false)
    .configure(YAMLGenerator.Feature.SPLIT_LINES, false)
private val objectMapper: ObjectMapper = ObjectMapper(yaml)
    .registerKotlinModule()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)

inline fun <reified T : Any> readConfig(logger: Logger, configFile: Path): T =
    doReadConfig(T::class.java, logger, configFile)

fun <T : Any> doReadConfig(clazz: Class<T>, logger: Logger, configFile: Path): T {
    if (!configFile.exists()) {
        logger.info("No config file found, creating")
        // NOTE this is an empty YAML dictionary, it will get populated from default config
        // also, hack
        configFile.writeText("{}")
    }
    val node = objectMapper.readTree(configFile.toFile()) as? ObjectNode
        ?: throw Exception("Config root has to be an object")
    val version = parseVersion(node)
    node.remove(CONFIG_VERSION_PROPERTY)
    when {
        version > currentConfigVersion -> throw Exception("Config version is greater than supported")
        version < currentConfigVersion -> {
            logger.info("Migrating config from version $version to $currentConfigVersion")
            val backupName = "backup_config_ver$version.yml"
            // throws if file exists
            configFile.copyTo(configFile.resolveSibling(backupName), overwrite = false)
            logger.info("Made config backup $backupName")
            runMigrations(node, version)
            logger.info("Migrations complete")
        }
    }
    return objectMapper.convertValue(node, clazz).also {
        // write migrated + defaulted config
        writeConfig(it, configFile)
    }
}

fun writeConfig(config: Any, f: Path) {
    val node = objectMapper.convertValue<ObjectNode>(config)
    node.replace(CONFIG_VERSION_PROPERTY, objectMapper.nodeFactory.numberNode(currentConfigVersion))
    objectMapper.writeValue(f.toFile(), node)
}

private fun runMigrations(config: ObjectNode, version: ConfigVersion) {
    for (ver in version..<currentConfigVersion) {
        migrations[ver](config)
    }
}

private fun parseVersion(config: JsonNode): ConfigVersion {
    // missing version -> assume 0 (before config refactor)
    val versionNode = config.get(CONFIG_VERSION_PROPERTY) ?: return 0
    if (!versionNode.isInt) throw Exception("'configVersion' should be an integer")
    return versionNode.intValue()
}

private typealias Migration = ObjectNode.() -> Unit

private fun ObjectNode.removeMany(vararg properties: String) {
    remove(properties.toList())
}

private fun ObjectNode.getObject(prop: String): ObjectNode =
    get(prop) as? ObjectNode ?: throw Exception("$prop was not an object")

private val removeUnnecessaryStuff: Migration = {
    getObject("format").removeMany(
        "chattore", "error", "funcommandsDefault", "funcommandsNoCommands",
        "funcommandsHeader", "funcommandsCommandInfo", "funcommandsMissingCommand", "funcommandsCommandNotFound",
        "mailSent", "mailReceived", "mailUnread", "playerProfile", "socialSpy", "commandSpy",
        "chatConfirmPrompt", "chatConfirm", "messageReceived", "messageSent", "help",
    )
}

private val migrations = arrayOf<Migration>(
    removeUnnecessaryStuff,
)

private val currentConfigVersion: ConfigVersion = migrations.size
private const val CONFIG_VERSION_PROPERTY = "configVersion"
