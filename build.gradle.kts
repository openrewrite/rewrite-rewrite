plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
    id("org.openrewrite.build.moderne-source-available-license") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "A rewrite module automating best practices and major version migrations for OpenRewrite recipes"

recipeDependencies {
    parserClasspath("org.openrewrite:rewrite-core:latest.release")
    parserClasspath("org.openrewrite:rewrite-java:latest.release")
}

val inlineRewriteMethods = layout.projectDirectory.file("src/main/resources/META-INF/rewrite/inline-rewrite-methods.yml")
tasks.named("createTypeTable") {
    // Read when the task runs, so the type tables follow the artifacts the recipe file was just written to name
    doFirst {
        InlineMethodsClasspath.coordinates(inlineRewriteMethods.asFile)
            .forEach { recipeDependencies.parserClasspath("$it:latest.release") }
    }
    doLast {
        InlineMethodsClasspath.pinMajorVersions(inlineRewriteMethods.asFile,
            layout.projectDirectory.file("src/main/resources/META-INF/rewrite/classpath.tsv.gz").asFile)
    }
}

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    implementation(platform("org.openrewrite:rewrite-bom:$rewriteVersion"))

    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite:rewrite-gradle")
    implementation("org.openrewrite:rewrite-maven")
    implementation("org.openrewrite:rewrite-yaml")
    implementation("org.openrewrite.recipe:rewrite-migrate-java:$rewriteVersion")
    implementation("org.openrewrite.recipe:rewrite-static-analysis:$rewriteVersion")
    implementation("org.openrewrite.recipe:rewrite-testing-frameworks:$rewriteVersion")

    annotationProcessor("org.openrewrite:rewrite-templating:$rewriteVersion")
    implementation("org.openrewrite:rewrite-templating:$rewriteVersion")
    compileOnly("com.google.errorprone:error_prone_core:latest.release") {
        exclude("com.google.auto.service", "auto-service-annotations")
        exclude("io.github.eisop","dataflow-errorprone")
    }

    implementation("org.yaml:snakeyaml:latest.release")

    runtimeOnly("org.openrewrite.recipe:rewrite-java-dependencies:$rewriteVersion")

    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.openrewrite:rewrite-kotlin")
    testImplementation("org.junit-pioneer:junit-pioneer:2.3.0")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Arewrite.javaParserClasspathFrom=resources")
}

val downloadDeprecatedMethodDelegations = tasks.register<DownloadDeprecatedMethodDelegations>("downloadDeprecatedMethodDelegations") {
    group = "generation"
    description = "Runs `FindDeprecatedMethodDelegations` in Moderne on `-PmoderneOrganization`, by default Moderne + OpenRewrite, " +
            "optionally from a `-PmoderneBundle` that `mod config recipes bundle push .` printed, and downloads its data table."
    organization.set(providers.gradleProperty("moderneOrganization").orElse("Moderne + OpenRewrite"))
    bundle.set(providers.gradleProperty("moderneBundle"))
    delegations.set(layout.buildDirectory.file("deprecated-method-delegations.csv"))
}

tasks.register<UpdateInlineRewriteMethods>("updateInlineRewriteMethods") {
    group = "generation"
    description = "Merges an export of the `DeprecatedMethodDelegations` data table into `inline-rewrite-methods.yml`, " +
            "passed as `-PdelegationsCsv=<file>`, or else downloaded by `downloadDeprecatedMethodDelegations`, " +
            "then regenerates the type tables its `classpathFromResources` name."
    val delegationsCsv = providers.gradleProperty("delegationsCsv")
    if (delegationsCsv.isPresent) {
        delegations.set(layout.projectDirectory.file(delegationsCsv.get()))
    } else {
        delegations.set(downloadDeprecatedMethodDelegations.flatMap { it.delegations })
    }
    recipeFile.set(inlineRewriteMethods)
    finalizedBy("createTypeTable")
}
