import org.jetbrains.intellij.platform.gradle.TestFrameworkType

dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.frontend")
        bundledModule("intellij.platform.rpc")
        compileOnly(libs.kotlin.serialization.core.jvm)
        compileOnly(libs.kotlin.serialization.json.jvm)
        testFramework(TestFrameworkType.Platform)
    }
    implementation(project(":shared"))

    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)
}

// Svelte player UI is built from the repo-root ui/ tree into this module's resources.
val buildPlayerUi by tasks.registering(Exec::class) {
    workingDir = rootProject.file("ui")
    commandLine("bash", "-lc", "npm run build")
    inputs.dir(rootProject.file("ui/src"))
    inputs.file(rootProject.file("ui/index.html"))
    inputs.file(rootProject.file("ui/vite.config.ts"))
    inputs.file(rootProject.file("ui/package.json"))
    outputs.file(layout.projectDirectory.file("src/main/resources/player/index.html"))
}

tasks.processResources {
    dependsOn(buildPlayerUi)
}
