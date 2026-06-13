# Jetplay

IntelliJ Platform plugin providing native audio/video playback in JetBrains IDEs using JCEF (embedded Chromium).

## Tech Stack

- Kotlin
- Gradle (Kotlin DSL)
- IntelliJ Platform SDK (2025.2+)
- JCEF (JBCefBrowser) for media rendering
- JDK 21

## Project Structure

- `shared/`, `frontend/`, `backend/` — Plugin Model V2 content modules (shared types + RPC contract; frontend editor/JCEF player/loopback media server; backend ffmpeg + byte access), each under `<module>/src/main/kotlin/dev/twango/jetplay/`
- `src/main/resources/META-INF/plugin.xml` — root plugin descriptor (content-module wiring only)
- `frontend/src/main/resources/dev.twango.jetplay.frontend.xml` — frontend module descriptor; single source of truth for supported extensions (`fileType`/`fileEditorProvider` are frontend-side, so they live here, not in the root descriptor)
- `gradle.properties` — plugin metadata and version config

## Conventions

- Follow IntelliJ Platform plugin conventions and API patterns
- Use `FileEditorProvider` / `FileEditor` for registering custom editors
- Keep the plugin lightweight — no unnecessary services or actions
- Supported extensions are defined in the frontend module descriptor (`dev.twango.jetplay.frontend.xml`), not hardcoded in Kotlin

## Build

```
./gradlew build        # compile + test + verify
./gradlew runIde       # launch sandbox IDE with plugin
```