import java.io.File
import java.util.zip.GZIPInputStream

object InlineMethodsClasspath {
    // A `classpathFromResources` item, as `artifactId-` or with a major version pinned; Java imports never end like that
    private val PREFIX = Regex("""^(\s+- )'([\w.-]+?)-\d*'$""", RegexOption.MULTILINE)

    // The artifacts the `classpathFromResources` of generated `InlineMethods` steps name, with groupIds from
    // the last download, or else the type table already there
    fun coordinates(recipeFile: File, downloaded: File, typeTable: File): Set<String> {
        val groupIds = typeTableRows(typeTable).associate { it[1] to it[0] } +
                (if (downloaded.exists()) downloaded.readLines().filter { it.isNotBlank() }
                    .associate { it.substringAfter(':') to it.substringBefore(':') } else emptyMap())
        return PREFIX.findAll(recipeFile.readText()).map { it.groupValues[2] }
            .map { "${groupIds[it] ?: error("No groupId for $it; run updateInlineRewriteMethods")}:$it" }
            .toSortedSet()
    }

    // So that `rewrite-java-` can not also match a `rewrite-java-dependencies` type table
    fun pinMajorVersions(recipeFile: File, typeTable: File) {
        val versions = typeTableRows(typeTable).associate { it[1] to it[2] }
        recipeFile.writeText(PREFIX.replace(recipeFile.readText()) {
            val (indent, artifactId) = it.destructured
            "$indent'$artifactId-${(versions[artifactId] ?: error("No type table for $artifactId")).substringBefore('.')}'"
        })
    }

    private fun typeTableRows(typeTable: File): List<List<String>> =
        GZIPInputStream(typeTable.inputStream()).bufferedReader().useLines { lines -> lines.drop(1).map { it.split('\t') }.toList() }
}
