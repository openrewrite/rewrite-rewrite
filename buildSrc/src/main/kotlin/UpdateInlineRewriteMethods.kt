import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.util.zip.GZIPInputStream

abstract class UpdateInlineRewriteMethods : DefaultTask() {
    @get:InputFile
    abstract val delegations: RegularFileProperty

    @get:InputFile
    abstract val typeTable: RegularFileProperty

    @get:Internal
    abstract val recipeFile: RegularFileProperty

    @TaskAction
    fun update() {
        val yaml = recipeFile.get().asFile
        val text = yaml.readText()
        val recipeListEnd = text.indexOf("recipeList:\n") + "recipeList:\n".length

        // Keep entries the scan no longer finds, as callers often build against releases from before a method was removed
        val entries = existingEntries(text.substring(recipeListEnd))
        val rows = parseCsv(delegations.get().asFile.readLines().dropWhile { it.startsWith("#") }.joinToString("\n"))
        val header = rows.first()
        rows.drop(1)
            .map { header.zip(it).toMap() }
            .filter { row -> row.getValue("methodPattern").let { it.startsWith(INCLUDED_PACKAGES) && !it.startsWith(EXCLUDED_PACKAGES) } }
            .sortedWith(compareBy({ it.getValue("repositoryPath") }, { it.getValue("methodPattern") }))
            .forEach { row ->
                val repository = row.getValue("repositoryPath")
                val methodPattern = row.getValue("methodPattern")
                var entry = row.getValue("recipeYaml")
                // Without the types of what a replacement imports, `InlineMethodCalls` does not add those imports;
                // the class a constructor call names is already imported, and the JDK is always on the classpath
                val constructedClass = methodPattern.substringBefore(" ").substringBefore('$').takeIf { " <constructor>(" in methodPattern }
                if (yamlList(entry, "imports").any { !it.startsWith("java.") && it != constructedClass }) {
                    val classpath = CLASSPATH_FROM_RESOURCES[repository]
                        ?: error("Add `parserClasspath` type tables for $repository to CLASSPATH_FROM_RESOURCES")
                    entry += "\n    classpathFromResources:" + classpath.joinToString("") { "\n      - '$it'" }
                }
                entries[methodPattern] = repository to entry.lines().joinToString("\n") { "  $it" }
            }

        val typeTables = typeTables()
        val missing = entries.values.flatMap { yamlList(it.second, "classpathFromResources") }.toSortedSet()
            .filter { prefix -> typeTables.none { it.startsWith(prefix) } }
        if (missing.isNotEmpty()) {
            throw GradleException("No type table in ${typeTable.get().asFile.name} begins with ${missing.joinToString()}; " +
                    "add it to `parserClasspath` and run `./gradlew createTypeTable`")
        }

        val byRepository = entries.entries.groupBy({ it.value.first }, { it.key to it.value.second }).toSortedMap()
        yaml.writeText(text.substring(0, recipeListEnd) + byRepository.entries.joinToString("") { (repository, patternEntries) ->
            "\n  # $repository\n" + patternEntries.sortedBy { it.first }.joinToString("") { it.second + "\n" }
        })
        logger.lifecycle("Wrote ${entries.size} entries from ${byRepository.size} repositories to $yaml")
    }

    private fun yamlList(entry: String, key: String): List<String> =
        entry.lines()
            .dropWhile { it.trimStart() != "$key:" }
            .drop(1)
            .takeWhile { it.trimStart().startsWith("- ") }
            .map { it.trimStart().removePrefix("- ").trim('\'') }

    // The `artifactId-version` names that `classpathFromResources` prefixes match
    private fun typeTables(): Set<String> =
        GZIPInputStream(typeTable.get().asFile.inputStream()).bufferedReader().useLines { lines ->
            lines.drop(1).map { it.split('\t') }.map { "${it[1]}-${it[2]}" }.toSet()
        }

    private fun existingEntries(recipeList: String): MutableMap<String, Pair<String, String>> {
        val entries = mutableMapOf<String, Pair<String, String>>()
        var repository = ""
        val block = mutableListOf<String>()
        fun flush() {
            if (block.isNotEmpty()) {
                val entry = block.joinToString("\n")
                val pattern = METHOD_PATTERN.find(entry)!!.groupValues[1].replace("''", "'")
                entries[pattern] = repository to entry
                block.clear()
            }
        }
        for (line in recipeList.lines()) {
            when {
                line.startsWith("  # ") -> { flush(); repository = line.removePrefix("  # ") }
                line == "  - org.openrewrite.java.InlineMethodCalls:" -> { flush(); block += line }
                line.isNotBlank() -> block += line
            }
        }
        flush()
        return entries
    }

    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { row += field.toString(); field.clear() }
                !quoted && c == '\n' -> { row += field.toString().removeSuffix("\r"); field.clear(); rows += row; row = mutableListOf() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString()
            rows += row
        }
        return rows
    }

    private fun String.startsWith(prefixes: List<String>) = prefixes.any { startsWith(it) }

    private companion object {
        val INCLUDED_PACKAGES = listOf("org.openrewrite.", "io.moderne.")
        // Forks of third party projects keep their own deprecations
        val EXCLUDED_PACKAGES = listOf("org.openrewrite.jgit.")
        val CLASSPATH_FROM_RESOURCES = mapOf(
            "moderneinc/jsonrpc" to listOf("jsonrpc-"),
            "moderneinc/rewrite-program-analysis" to listOf("rewrite-core-8", "rewrite-program-analysis-"),
            "openrewrite/rewrite" to listOf("rewrite-core-8", "rewrite-java-8", "rewrite-python-8"),
        )
        val METHOD_PATTERN = Regex("""^\s+methodPattern: '((?:[^']|'')*)'$""", RegexOption.MULTILINE)
    }
}
