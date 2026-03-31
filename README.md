# jetplay

![Build](https://github.com/twangodev/jetplay/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

<!-- Plugin description -->
Audio and video playback for JetBrains IDEs. Open media files directly in the editor tab without needing an external player.

**Supported formats:**

| Video               | Audio                                    |
|---------------------|------------------------------------------|
| MP4, M4V, WebM, OGV | MP3, WAV, OGG, OGA, Opus, M4A, AAC, FLAC |
<!-- Plugin description end -->

## Features

- Open media files inline in the editor tab — just double-click in the project tree
- Built-in HTML5 playback controls (play/pause, seek, volume, fullscreen)
- Automatic IDE theme integration
- Works with all JetBrains IDEs (IntelliJ IDEA, PyCharm, WebStorm, etc.)
- Powered by JCEF (embedded Chromium) — no external dependencies

## Installation

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "jetplay"</kbd> >
  <kbd>Install</kbd>

- Manually:

  Download the [latest release](https://github.com/twangodev/jetplay/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

## Building from Source

```bash
./gradlew build        # compile + test + verify
./gradlew runIde       # launch sandbox IDE with plugin loaded
```

Requires JDK 21.
