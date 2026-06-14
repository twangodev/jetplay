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
        listOf(
            ideHome.resolve("plugins/cwm-plugin/lib/frontend-split/rd-client.jar"),
            ideHome.resolve("plugins/cwm-plugin/lib/frontend-split/frontend-split.jar"),
            ideHome.resolve("lib/intellij.rd.platform.jar"),
            ideHome.resolve("lib/intellij.rd.ui.jar"),
            ideHome.resolve("lib/intellij.rd.ide.model.generated.jar"),
            ideHome.resolve("lib/intellij.libraries.rd.core.jar"),
        )
    }))

    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)
}

// Align the content-module jar name with the module id so the verifier/platform resolve the descriptor.
tasks.named<org.jetbrains.intellij.platform.gradle.tasks.ComposedJarTask>("composedJar") {
    archiveBaseName.set("dev.twango.jetplay.frontend.split")
}
