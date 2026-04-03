<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# jetplay Changelog

## [0.2.0](https://github.com/twangodev/jetplay/compare/v0.1.1...v0.2.0) (2026-04-03)


### Added

* add i18n support, FFmpeg availability check, and balloon notifications ([7a994f0](https://github.com/twangodev/jetplay/commit/7a994f0c16529a51f5f2c288c8985c5f8cbe28a9))
* add remote media playback support ([6c74205](https://github.com/twangodev/jetplay/commit/6c7420595af463b609af2a9cfb66d3322c25a90c))


### Fixed

* downgrade Kotlin version from 2.3.20 to 2.1.20 in libs.versions.toml ([e4c4e56](https://github.com/twangodev/jetplay/commit/e4c4e56bb9240259ad70eb1e3dfbc3d8fe47c333))
* **i18n:** pass translated UI strings to Svelte instead of hardcoded English text ([0ca637e](https://github.com/twangodev/jetplay/commit/0ca637e6fb6ef21f05655edf8de187ea6972137f))


### Changed

* decompose MediaFileEditor into modular package architecture ([166c220](https://github.com/twangodev/jetplay/commit/166c220675bdc801d9b56c423c8a79ec909dff2c))
* rearrange time display and file name layout in VideoPlayer component ([cd79186](https://github.com/twangodev/jetplay/commit/cd79186b4851607b800a2caa025241b5af76e0f7))

## [0.1.1](https://github.com/twangodev/jetplay/compare/v0.1.0...v0.1.1) (2026-04-02)


### Fixed

* lower minimum compatibility to IntelliJ 2022.3 ([ea125a8](https://github.com/twangodev/jetplay/commit/ea125a81bb6e803d3c59574fc96c1015f3eb392c))

## [0.1.0](https://github.com/twangodev/jetplay/compare/v0.0.1...v0.1.0) (2026-04-02)


### Added

* add Branding component to enhance UI across audio and video players ([9cc2f1c](https://github.com/twangodev/jetplay/commit/9cc2f1c44289f61fd0c4296bfca3a48186096352))
* add media file editor and provider for audio/video files ([def1d5b](https://github.com/twangodev/jetplay/commit/def1d5b0f63cc446e1be211fc4c01eaacb72e5e1))
* add Node.js setup and UI dependency installation to build and release workflows ([d7bfca3](https://github.com/twangodev/jetplay/commit/d7bfca3ce5e623d29fc73597b831e4a91ed9c53b))
* enhance media player with transcoding support and improved UI ([53a3c2d](https://github.com/twangodev/jetplay/commit/53a3c2db3a0febbd7dfc341eedad6bfe3ca82702))
* enhance media player with transcoding support and UI improvements ([9128bcf](https://github.com/twangodev/jetplay/commit/9128bcff00a56690f590ea5b10c404a6adbe0918))
* expand supported media formats for enhanced playback capabilities ([ece46c5](https://github.com/twangodev/jetplay/commit/ece46c5d13017a08a0b2a64196a7405ead4cc93d))
* implement media file type support and update editor provider ([ca4fd75](https://github.com/twangodev/jetplay/commit/ca4fd7520ac3e7f8514272c1b3cb284e9493c4da))
* implement media player with audio and video support, including loading and error states ([469fb4c](https://github.com/twangodev/jetplay/commit/469fb4c716e92321937b6b6418571f2927b28c7b))
* implement media transcoding support and add unit tests for format checks ([0c094af](https://github.com/twangodev/jetplay/commit/0c094af2043f23954afcde11a7557ae2a85b81dd))
* initialize Svelte project with TypeScript and Vite setup ([30e64b6](https://github.com/twangodev/jetplay/commit/30e64b652b468d6076255093c23b0e0ce9d89597))


### Changed

* adjust global type declarations for jetplay and move to separate file ([40e423c](https://github.com/twangodev/jetplay/commit/40e423c0b3197e53ef5f24e75bf0e1278f4c19ef))

## [Unreleased]
### Added
- Initial project setup
