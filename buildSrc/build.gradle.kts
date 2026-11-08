plugins {
    `kotlin-dsl`
}

repositories {
    // The Maven cache the rewrite build plugins resolve Maven Central through, which they can not do for buildSrc
    val codegenomeUsername = providers.gradleProperty("codegenomeUsername").orNull ?: System.getenv("CODEGENOME_USERNAME")
    val codegenomePassword = providers.gradleProperty("codegenomePassword").orNull ?: System.getenv("CODEGENOME_TOKEN")
    if (!codegenomeUsername.isNullOrEmpty() && !codegenomePassword.isNullOrEmpty()) {
        maven {
            name = "codegenomeMavenPublic"
            url = uri("https://artifacts.codegenomeproject.org/maven-public/")
            credentials {
                username = codegenomeUsername
                password = codegenomePassword
            }
            mavenContent { releasesOnly() }
        }
    }
    mavenCentral()
}
