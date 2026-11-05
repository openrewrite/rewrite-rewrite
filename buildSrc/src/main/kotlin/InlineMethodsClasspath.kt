import java.io.File
import java.util.zip.GZIPInputStream

object InlineMethodsClasspath {
    private val COMMENTED_COORDINATES = Regex("""^(\s+- )'[^']+' # ([\w.-]+):([\w.-]+)$""", RegexOption.MULTILINE)

    // The artifacts that the `classpathFromResources` of generated `InlineMethods` steps name in a trailing comment
    fun coordinates(recipeFile: File): Set<String> =
        COMMENTED_COORDINATES.findAll(recipeFile.readText()).map { "${it.groupValues[2]}:${it.groupValues[3]}" }.toSortedSet()

    // Pins each `classpathFromResources` prefix to the major version of its artifact in the type table, so that
    // `rewrite-java-` can not also match a `rewrite-java-dependencies` type table
    fun pinMajorVersions(recipeFile: File, typeTable: File) {
        val versions = GZIPInputStream(typeTable.inputStream()).bufferedReader().useLines { lines ->
            lines.drop(1).map { it.split('\t') }.associate { "${it[0]}:${it[1]}" to it[2] }
        }
        recipeFile.writeText(COMMENTED_COORDINATES.replace(recipeFile.readText()) {
            val (indent, groupId, artifactId) = it.destructured
            val version = versions["$groupId:$artifactId"] ?: error("No type table for $groupId:$artifactId")
            "$indent'$artifactId-${version.substringBefore('.')}' # $groupId:$artifactId"
        })
    }
}
