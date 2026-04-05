import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intelliJPlatform)
    alias(libs.plugins.detekt)
    alias(libs.plugins.qodana)
    alias(libs.plugins.kover)
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("org.bytedeco:javacv:1.5.13") {
        exclude(group = "org.bytedeco", module = "opencv")
        exclude(group = "org.bytedeco", module = "openblas")
        exclude(group = "org.bytedeco", module = "flycapture")
        exclude(group = "org.bytedeco", module = "libdc1394")
        exclude(group = "org.bytedeco", module = "libfreenect")
        exclude(group = "org.bytedeco", module = "libfreenect2")
        exclude(group = "org.bytedeco", module = "librealsense")
        exclude(group = "org.bytedeco", module = "librealsense2")
        exclude(group = "org.bytedeco", module = "videoinput")
        exclude(group = "org.bytedeco", module = "artoolkitplus")
        exclude(group = "org.bytedeco", module = "flandmark")
        exclude(group = "org.bytedeco", module = "leptonica")
        exclude(group = "org.bytedeco", module = "tesseract")
    }
    implementation("org.bytedeco:ffmpeg:7.1-1.5.13:linux-x86_64")
    implementation("org.bytedeco:ffmpeg:7.1-1.5.13:macosx-x86_64")
    implementation("org.bytedeco:ffmpeg:7.1-1.5.13:macosx-arm64")
    implementation("org.bytedeco:ffmpeg:7.1-1.5.13:windows-x86_64")
    implementation("org.bytedeco:javacpp:1.5.13:linux-x86_64")
    implementation("org.bytedeco:javacpp:1.5.13:macosx-x86_64")
    implementation("org.bytedeco:javacpp:1.5.13:macosx-arm64")
    implementation("org.bytedeco:javacpp:1.5.13:windows-x86_64")

    detektPlugins(libs.detekt.formatting)

    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)

    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })
        bundledModules(providers.gradleProperty("platformBundledModules").map { it.split(',') })
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n")
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = providers.gradleProperty("pluginVersion").map {
            listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" })
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

kover {
    reports {
        total {
            xml {
                onCheck = true
            }
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$projectDir/detekt.yml"))
    basePath.set(projectDir)
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    reports {
        html.required.set(true)
        sarif.required.set(true)
    }
}

val buildPlayerUi by tasks.registering(Exec::class) {
    workingDir = file("ui")
    commandLine("bash", "-lc", "npm run build")
    inputs.dir("ui/src")
    inputs.file("ui/index.html")
    inputs.file("ui/vite.config.ts")
    inputs.file("ui/package.json")
    outputs.file("src/main/resources/player/index.html")
}

tasks {
    processResources {
        dependsOn(buildPlayerUi)
    }

    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
}
