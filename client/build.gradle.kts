import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.util.concurrent.Callable

val idesDir = rootProject.layout.projectDirectory.dir(".intellijPlatform/ides").asFile

dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.frontend")
        compileOnly(libs.kotlin.serialization.core.jvm)
        compileOnly(libs.kotlin.serialization.json.jvm)
        testFramework(TestFrameworkType.Platform)
    }
    implementation(project(":shared"))
    implementation(project(":frontend"))

    // These RD jars aren't exposed as bundledModule() in IU-261, so pin them as compileOnly — resolved
    // lazily via Callable because the IDE dir is empty until the platform plugin downloads it (fresh CI).
    compileOnly(files(Callable {
        val ideHome = idesDir.listFiles()?.sortedByDescending { it.name }?.firstOrNull()
            ?: error("No resolved IDE under $idesDir")
        // Glob the whole frontend-split dir instead of naming rd-client.jar/frontend-split.jar:
        // EAP builds rename these, and the split classes we compile against live somewhere in here.
        val splitDir = ideHome.resolve("plugins/cwm-plugin/lib/frontend-split")
        val splitJars = splitDir.listFiles { f -> f.extension == "jar" }?.toList().orEmpty()
        val libJars = listOf(
            "lib/intellij.rd.platform.jar",
            "lib/intellij.rd.ui.jar",
            "lib/intellij.rd.ide.model.generated.jar",
            "lib/intellij.libraries.rd.core.jar",
        ).map { ideHome.resolve(it) }
        // Fail early with a clear message if the IDE layout moved these internal jars.
        val missingLib = libJars.filterNot { it.exists() }
        require(splitJars.isNotEmpty() && missingLib.isEmpty()) {
            "Missing IntelliJ internal jars for :client under $ideHome:\n" +
                (if (splitJars.isEmpty()) "  $splitDir/*.jar\n" else "") +
                missingLib.joinToString("\n") { "  $it" }
        }
        splitJars + libJars
    }))

    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)
}

// Align the content-module jar name with the module id so the verifier/platform resolve the descriptor.
tasks.named<org.jetbrains.intellij.platform.gradle.tasks.ComposedJarTask>("composedJar") {
    archiveBaseName.set("dev.twango.jetplay.client")
}
