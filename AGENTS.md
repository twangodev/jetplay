# Jetplay

IntelliJ Platform plugin providing native audio/video playback in JetBrains IDEs using JCEF (embedded Chromium).

## Tech Stack

- Kotlin
- Gradle (Kotlin DSL)
- IntelliJ Platform SDK (2025.2+)
- JCEF (JBCefBrowser) for media rendering
- JDK 21

## Project Structure

- `src/main/kotlin/dev/twango/jetplay/` — plugin source
- `src/main/resources/META-INF/plugin.xml` — plugin descriptor
- `gradle.properties` — plugin metadata and version config

## Conventions

- Follow IntelliJ Platform plugin conventions and API patterns
- Use `FileEditorProvider` / `FileEditor` for registering custom editors
- Keep the plugin lightweight — no unnecessary services or actions
- Supported formats: MP4, WebM, MP3, OGG, WAV

## Build

```
./gradlew build        # compile + test + verify
./gradlew runIde       # launch sandbox IDE with plugin
```