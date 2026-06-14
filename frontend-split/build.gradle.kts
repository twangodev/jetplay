import org.jetbrains.intellij.platform.gradle.TestFrameworkType

// The rdclient / rd jars below aren't exposed as bundled modules in IU-261,
// so pin them as compileOnly from the portably-resolved IDE home.
val ideHome = rootProject.layout.projectDirectory.dir(".intellijPlatform/ides").asFile
    .listFiles()?.sortedByDescending { it.name }?.firstOrNull()
    ?: error("No resolved IDE under .intellijPlatform/ides; run a Gradle build first")

dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.frontend")
        compileOnly(libs.kotlin.serialization.core.jvm)
        compileOnly(libs.kotlin.serialization.json.jvm)
        testFramework(TestFrameworkType.Platform)
    }
    implementation(project(":shared"))
    implementation(project(":frontend"))

    compileOnly(files(
        ideHome.resolve("plugins/cwm-plugin/lib/frontend-split/rd-client.jar"),
        ideHome.resolve("plugins/cwm-plugin/lib/frontend-split/frontend-split.jar"),
        ideHome.resolve("lib/intellij.rd.platform.jar"),
        ideHome.resolve("lib/intellij.rd.ui.jar"),
        ideHome.resolve("lib/intellij.rd.ide.model.generated.jar"),
        ideHome.resolve("lib/intellij.libraries.rd.core.jar"),
    ))

    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)
}

// Align the content-module jar name with the module id so the verifier/platform resolve the descriptor.
tasks.named<org.jetbrains.intellij.platform.gradle.tasks.ComposedJarTask>("composedJar") {
    archiveBaseName.set("dev.twango.jetplay.frontend.split")
}
