package chattore

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

typealias ConfigVersion = Int

private val yaml = YAMLFactory()
    .configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false)
    .configure(YAMLGenerator.Feature.SPLIT_LINES, false)
val objectMapper: ObjectMapper = ObjectMapper(yaml)
    .registerKotlinModule()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)

inline fun <reified T> readConfig(f: File) = readConfig<T>(objectMapper.readTree(f))
inline fun <reified T> readConfig(node: JsonNode): T {
    node as? ObjectNode ?: throw Exception("Config root has to be an object")
    val version = parseVersion(node)
    node.remove(CONFIG_VERSION_PROPERTY)
    doMigrations(node, version)
    return objectMapper.convertValue(node)
}

fun writeConfig(config: Any, f: File) {
    val node = objectMapper.convertValue<ObjectNode>(config)
    node.replace(CONFIG_VERSION_PROPERTY, objectMapper.nodeFactory.numberNode(currentConfigVersion))
    objectMapper.writeValue(f, node)
}

fun doMigrations(config: ObjectNode, version: ConfigVersion) {
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

