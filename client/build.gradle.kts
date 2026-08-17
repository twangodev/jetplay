import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension

val rdClientLibrary = providers.provider {
    val ideHome = extensions.getByType<IntelliJPlatformExtension>().platformPath
    val clientCandidates = listOf(
        "lib/intellij.rd.client.jar", // 2026.2+
        "plugins/cwm-plugin/lib/frontend-split/rd-client.jar", // 2025.3-2026.1
    )
    clientCandidates.firstNotNullOfOrNull { path ->
        ideHome.resolve(path).takeIf { it.toFile().isFile }?.toFile()
    }
        ?: error(
            "Missing IntelliJ RD client library under $ideHome; checked:\n" +
                clientCandidates.joinToString("\n") { "  $it" },
        )
}

dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.frontend")
        bundledModule("intellij.rd.ide.model.generated")
        compileOnly(libs.kotlin.serialization.core.jvm)
        compileOnly(libs.kotlin.serialization.json.jvm)
        testFramework(TestFrameworkType.Platform)
    }
    implementation(project(":shared"))
    implementation(project(":frontend"))

    // Not indexed as a bundled module until 2026.2; the class is binary-compatible across the move.
    compileOnly(files(rdClientLibrary))

    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)
}

// Align the content-module jar name with the module id so the verifier/platform resolve the descriptor.
tasks.named<org.jetbrains.intellij.platform.gradle.tasks.ComposedJarTask>("composedJar") {
    archiveBaseName.set("dev.twango.jetplay.client")
}
