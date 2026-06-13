import org.jetbrains.intellij.platform.gradle.TestFrameworkType

// STEP 1 ONLY: bytedeco lives here temporarily while all code is in shared.
// Step 2 moves it to backend/build.gradle.kts; shared then keeps only serialization.
// Tests are also parked here temporarily; Step 5 re-homes them per module.
dependencies {
    intellijPlatform {
        compileOnly(libs.kotlin.serialization.core.jvm)
        compileOnly(libs.kotlin.serialization.json.jvm)
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)
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
}
