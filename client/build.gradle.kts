import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension

val rdClientLibraries = providers.provider {
    val ideHome = extensions.getByType<IntelliJPlatformExtension>().platformPath
    val clientCandidates = listOf(
        "lib/intellij.rd.client.jar", // 2026.2+
        "plugins/cwm-plugin/lib/frontend-split/rd-client.jar", // 2025.3-2026.1
    )
    val clientLibrary = clientCandidates.firstOrNull { ideHome.resolve(it).toFile().isFile }
        ?: error(
            "Missing IntelliJ RD client library under $ideHome; checked:\n" +
                clientCandidates.joinToString("\n") { "  $it" },
        )
    val libraryPaths = listOf(
        clientLibrary,
        "lib/intellij.rd.platform.jar",
        "lib/intellij.rd.ui.jar",
        "lib/intellij.rd.ide.model.generated.jar",
        "lib/intellij.libraries.rd.core.jar",
    )
    val missingLibraries = libraryPaths.filterNot { ideHome.resolve(it).toFile().isFile }
    require(missingLibraries.isEmpty()) {
        "Missing IntelliJ RD libraries under $ideHome:\n" +
            missingLibraries.joinToString("\n") { "  $it" }
    }
    libraryPaths.map { ideHome.resolve(it).toFile() }
}

dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.frontend")
        compileOnly(libs.kotlin.serialization.core.jvm)
        compileOnly(libs.kotlin.serialization.json.jvm)
        testFramework(TestFrameworkType.Platform)
    }
    implementation(project(":shared"))
    implementation(project(":frontend"))

    // The file-editor handler remains binary-compatible, but its jar moved out of the CWM plugin
    // in 2026.2. Resolve it from the actual platform instead of scanning a pre-populated IDE cache.
    compileOnly(files(rdClientLibraries))

    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)
}

// Align the content-module jar name with the module id so the verifier/platform resolve the descriptor.
tasks.named<org.jetbrains.intellij.platform.gradle.tasks.ComposedJarTask>("composedJar") {
    archiveBaseName.set("dev.twango.jetplay.client")
}
