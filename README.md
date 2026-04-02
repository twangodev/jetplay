# jetplay

![Build](https://github.com/twangodev/jetplay/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/31014.svg?logo=jetbrains)](https://plugins.jetbrains.com/plugin/31014)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/31014.svg)](https://plugins.jetbrains.com/plugin/31014)

<!-- Plugin description -->
Audio and video playback for JetBrains IDEs, powered by JCEF and FFmpeg.

Supported formats: MP4, M4V, MKV, AVI, MOV, WMV, FLV, WebM, OGV, MP3, WAV, OGG, OGA, Opus, M4A, AAC, WMA, AIFF, FLAC
<!-- Plugin description end -->

## Format Support Matrix

|  | Native | Transcoded |
|--|--------|------------|
| **Video** | WebM, OGV | MP4, M4V, MKV, AVI, MOV, WMV, FLV |
| **Audio** | MP3, WAV, FLAC, OGG, OGA, Opus | M4A, AAC, WMA, AIFF |

- **Native** — plays instantly in the embedded browser
- **Transcoded** — converted to WebM (VP9/Opus) via bundled FFmpeg on first open, with live progress

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

Requires JDK 21 and Node.js 22+.