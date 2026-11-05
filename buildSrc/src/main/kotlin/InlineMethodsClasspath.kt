// Artifacts whose types generated replacements import, by the repository declaring the deprecated method.
// They become `parserClasspath` type tables, which those replacements read through `classpathFromResources`.
object InlineMethodsClasspath {
    val ARTIFACTS: Map<String, List<String>> = mapOf(
        "moderneinc/jsonrpc" to listOf("io.moderne:jsonrpc"),
        "moderneinc/rewrite-program-analysis" to listOf("org.openrewrite:rewrite-core", "io.moderne.recipe:rewrite-program-analysis"),
        "openrewrite/rewrite" to listOf("org.openrewrite:rewrite-core", "org.openrewrite:rewrite-java", "org.openrewrite:rewrite-python"),
    )
}
