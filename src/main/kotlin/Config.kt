package chattore

import chattore.entity.ChattOREConfig
import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.convertValue
import java.io.File

// config goals
// - each feature can have its own configuration data class
// - config migrations, both from "legacy" config or from previous version


val migrations = arrayOf<Migration>()
val currentConfigVersion = migrations.size

fun readConfig(konfs: List<Konfig<*>>, s: String) = readConfig(konfs, objectMapper.readTree(s))
fun readConfig(konfs: List<Konfig<*>>, f: File) = readConfig(konfs, objectMapper.readTree(f))
fun readConfig(konfs: List<Konfig<*>>, node: JsonNode): ObjectNode {
    val configNode = node as? ObjectNode ?: throw Exception("Config root has to be an object")
    val def = buildDefaultConfig(konfs)
    val version = parseVersion(configNode)
    doMigrations(configNode, version)
    // don't merge lists by default
    objectMapper.configOverride(ArrayNode::class.java).setMergeable(false)
    objectMapper.readerForUpdating(def).readValue<JsonNode>(configNode)
    return configNode
}

fun doMigrations(config: ObjectNode, version: ConfigVersion) {
    for (ver in version..<currentConfigVersion) {
        migrations[ver].run(config)
    }
}

typealias ConfigVersion = Int

fun parseVersion(config: JsonNode): ConfigVersion {
    // missing version -> assume 0 (before config refactor)
    val versionNode = config.get("configVersion") ?: return 0
    if (!versionNode.isInt) throw Exception("'configVersion' should be an integer")
    return versionNode.intValue()
}

// note: Path only supports objects, not arrays
typealias Path = JsonPointer

class Konfig<T>(val path: Path, val klass: Class<T>, val default: T)

fun <T> ObjectNode.getValue(k: Konfig<T>): T = objectMapper.convertValue(this.at(k.path), k.klass)

inline fun <reified T> konf(prefix: Path, default: T): Konfig<T> = Konfig(prefix, T::class.java, default)

val objectMapper = ObjectMapper(YAMLFactory())

fun ObjectNode.getObj(prop: String): ObjectNode =
    when (val child = this.get(prop)) {
        is ObjectNode -> child
        null -> this.putObject(prop)
        else -> throw Exception("property $prop was not `ObjectNode?`")
    }

fun ObjectNode.getObj(ptr: Path): ObjectNode {
    var p = ptr
    var n = this
    while (!p.matches()) {
        val (prop, tail) = p.rootSplit()
        n = n.getObj(prop)
        p = tail
    }
    return n
}

fun ObjectNode.setAt(ptr: Path, value: JsonNode, replace: Boolean = false) {
    val (path, prop) = ptr.leafSplit()
    val parent = getObj(path)
    if (parent.has(prop) && !replace) throw Exception("overlapping stuff detected")
    parent.replace(prop, value)
}

fun ObjectNode.removeAt(p: JsonPointer) {
    val (path, prop) = p.leafSplit()
    getObj(path).remove(prop)
}

fun buildDefaultConfig(konfs: List<Konfig<*>>) = objectMapper.createObjectNode().apply {
    konfs.forEach {
        setAt(it.path, objectMapper.valueToTree(it.default))
    }
}

typealias Migration = (old: ObjectNode, new: ObjectNode) -> Unit

// I'm not sure whether this is a good idea or not
fun Migration.run(config: ObjectNode): Unit = this(config.deepCopy(), config)

fun Migration.then(next: Migration): Migration = { old, new ->
    this(old, new)
    next.run(new)
}

fun Migration.and(other: Migration): Migration = { old, new ->
    this(old, new)
    other(old, new)
}

// these work properly
// NOTE THAT last() RETURNS NULL ON empty!!! setAt is broken because of it
fun Path.rootSplit(): Pair<String, Path> = matchingProperty to tail()
fun Path.leafSplit(): Pair<Path, String> = head() to last().matchingProperty

object Migrations {
    val String.p
        get() = if (isEmpty())
            JsonPointer.empty()
        else
            JsonPointer.valueOf("/" + replace(".", "/"))

    fun rename(from: Path, to: Path): Migration = { orig, new ->
        new.removeAt(from)
        // TODO check if exists
        new.setAt(to, orig.at(from))
    }
}
