plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
    id("org.openrewrite.build.moderne-source-available-license") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "A rewrite module automating best practices and major version migrations for OpenRewrite recipes"

recipeDependencies {
    parserClasspath("org.openrewrite:rewrite-java:latest.release")
    parserClasspath("org.openrewrite:rewrite-maven:latest.release")
    parserClasspath("org.openrewrite:rewrite-gradle:latest.release")
}

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    implementation(platform("org.openrewrite:rewrite-bom:$rewriteVersion"))

    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite:rewrite-maven")
    implementation("org.openrewrite:rewrite-gradle")
    implementation("org.openrewrite.recipe:rewrite-migrate-java:$rewriteVersion")
    implementation("org.openrewrite.recipe:rewrite-static-analysis:$rewriteVersion")
    implementation("org.openrewrite.recipe:rewrite-testing-frameworks:$rewriteVersion")

    annotationProcessor("org.openrewrite:rewrite-templating:latest.release")
    implementation("org.openrewrite:rewrite-templating:$rewriteVersion")
    compileOnly("com.google.errorprone:error_prone_core:latest.release") {
        exclude("com.google.auto.service", "auto-service-annotations")
        exclude("io.github.eisop","dataflow-errorprone")
    }

    implementation("org.yaml:snakeyaml:latest.release")

    runtimeOnly("org.openrewrite.recipe:rewrite-java-dependencies:$rewriteVersion")

    testImplementation("org.openrewrite:rewrite-test")

    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.13.3")
    testImplementation("org.junit-pioneer:junit-pioneer:2.3.0")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Arewrite.javaParserClasspathFrom=resources")
}
