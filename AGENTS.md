# Jetplay

IntelliJ Platform plugin providing native audio/video playback in JetBrains IDEs using JCEF (embedded Chromium).

## Tech Stack

- Kotlin
- Gradle (Kotlin DSL)
- IntelliJ Platform SDK (2025.3+)
- JCEF (JBCefBrowser) for media rendering
- JDK 21

## Project Structure

- `shared/`, `frontend/`, `client/`, `backend/` — Plugin Model V2 content modules, each under `<module>/src/main/kotlin/dev/twango/jetplay/`. `shared` (loads everywhere): shared types, RPC contract, i18n bundle. `frontend` (loads on the Remote Dev host **and** client): file type + editor provider + JCEF player + loopback media server; JCEF is guarded off on the host. `client` (JetBrains Client only, binds the platform's `intellij.platform.frontend.split` module): `rdclient.fileEditorModelHandler` that renders the player client-side in split mode. `backend` (host): ffmpeg + RPC byte/transcode access.
- `src/main/resources/META-INF/plugin.xml` — root plugin descriptor (content-module wiring only)
- `frontend/src/main/resources/dev.twango.jetplay.frontend.xml` — frontend module descriptor; single source of truth for supported extensions. The `frontend` module loads on both host and client, so its `fileType`/`fileEditorProvider` register on the host (for detection/selection) while the JCEF player renders on the client.
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