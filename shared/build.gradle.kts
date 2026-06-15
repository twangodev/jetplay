import org.jetbrains.intellij.platform.gradle.TestFrameworkType

dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.rpc")
        compileOnly(libs.kotlin.serialization.core.jvm)
        compileOnly(libs.kotlin.serialization.json.jvm)
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)
}

// Align the content-module jar name with the module id ("lib/modules/<moduleId>.jar") so the verifier and
// platform resolve the descriptor instead of falling back to scanning every bundled jar.
tasks.named<org.jetbrains.intellij.platform.gradle.tasks.ComposedJarTask>("composedJar") {
    archiveBaseName.set("dev.twango.jetplay.shared")
}
