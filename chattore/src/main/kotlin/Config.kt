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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.copyTo

typealias ConfigVersion = Int

private val yaml = YAMLFactory()
    .configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false)
    .configure(YAMLGenerator.Feature.SPLIT_LINES, false)
val objectMapper: ObjectMapper = ObjectMapper(yaml)
    .registerKotlinModule()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)

val backupDateFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd_HH-mm-ss")

inline fun <reified T> readConfig(logger: Logger, f: Path): T {
    val node = objectMapper.readTree(f.toFile()) as? ObjectNode ?: throw Exception("Config root has to be an object")
    val version = parseVersion(node)
    node.remove(CONFIG_VERSION_PROPERTY)
    when {
        version > currentConfigVersion -> throw Exception("Config version is greater than supported")
        version < currentConfigVersion -> {
            logger.info("Migrating config from version $version to $currentConfigVersion")
            val backupName = "backup_config_ver$version-${backupDateFormatter.format(LocalDateTime.now())}.yml"
            f.copyTo(f.resolveSibling(backupName))
            logger.info("Made config backup $backupName")
            runMigrations(node, version)
            logger.info("Migrations complete")
        }
    }
    return objectMapper.convertValue<T>(node).also {
        // write migrated + defaulted config
        writeConfig(it as Any, f)
    }
}

fun writeConfig(config: Any, f: Path) {
    val node = objectMapper.convertValue<ObjectNode>(config)
    node.replace(CONFIG_VERSION_PROPERTY, objectMapper.nodeFactory.numberNode(currentConfigVersion))
    objectMapper.writeValue(f.toFile(), node)
}

fun runMigrations(config: ObjectNode, version: ConfigVersion) {
    for (ver in version..<currentConfigVersion) {
        migrations[ver](config)
    }
}

fun parseVersion(config: JsonNode): ConfigVersion {
    // missing version -> assume 0 (before config refactor)
    val versionNode = config.get(CONFIG_VERSION_PROPERTY) ?: return 0
    if (!versionNode.isInt) throw Exception("'configVersion' should be an integer")
    return versionNode.intValue()
}

typealias Migration = ObjectNode.() -> Unit

fun ObjectNode.removeMany(vararg properties: String) {
    remove(properties.toList())
}

fun ObjectNode.getObject(prop: String): ObjectNode =
    get(prop) as? ObjectNode ?: throw Exception("$prop was not an object")

val removeUnnecessaryStuff: Migration = {
    getObject("format").removeMany(
        "chattore", "error", "funcommandsDefault", "funcommandsNoCommands",
        "funcommandsHeader", "funcommandsCommandInfo", "funcommandsMissingCommand", "funcommandsCommandNotFound"
    )
}
val migrations = arrayOf<Migration>(
    removeUnnecessaryStuff,
)

val currentConfigVersion: ConfigVersion = migrations.size
const val CONFIG_VERSION_PROPERTY = "configVersion"
