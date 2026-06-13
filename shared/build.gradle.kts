import org.jetbrains.intellij.platform.gradle.TestFrameworkType

// Tests are parked here temporarily; Step 5 re-homes them per module.
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
