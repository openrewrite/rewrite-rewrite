import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

abstract class DownloadDeprecatedMethodDelegations : DefaultTask() {
    @get:Input
    abstract val organization: Property<String>

    @get:Input
    @get:Optional
    abstract val bundle: Property<String>

    @get:OutputFile
    abstract val delegations: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    init {
        // Each run picks up what the organization's repositories deprecated since the last one
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun download() {
        val run = mod(listOf("run", "moderne://${organization.get()}", "--recipe", RECIPE) +
                (bundle.orNull?.let { listOf("--bundle", it) } ?: emptyList()))
        val changeset = CHANGESET.find(run)?.groupValues?.get(1)
            ?: throw GradleException("Found no changeset in the output of `mod run`:\n$run")
        logger.lifecycle("Downloading the data table of changeset $changeset")
        mod(listOf("study", "moderne://changeset/$changeset", "--data-table", DATA_TABLE,
            "--csv", "--max", "10000", "--output-file", delegations.get().asFile.absolutePath))
    }

    private fun mod(args: List<String>): String {
        val output = ByteArrayOutputStream()
        execOperations.exec {
            commandLine(listOf("mod") + args)
            standardOutput = output
        }
        return output.toString(Charsets.UTF_8)
    }

    private companion object {
        const val RECIPE = "org.openrewrite.java.recipes.FindDeprecatedMethodDelegations"
        const val DATA_TABLE = "org.openrewrite.java.recipes.DeprecatedMethodDelegations"
        val CHANGESET = Regex("""moderne://changeset/(\S+)""")
    }
}
