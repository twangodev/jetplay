<script lang="ts">
  import IdeFrame from './IdeFrame.svelte'
  import VideoPlayer from '$lib/VideoPlayer.svelte'

  const config = window.jetplay ?? {}

  const tree = [
    { depth: 0, label: 'jetplay', kind: 'folder', expanded: true, bold: true },
    { depth: 1, label: '.github', kind: 'folder' },
    { depth: 1, label: 'assets', kind: 'folder', expanded: true },
    { depth: 2, label: 'sintel.mp4', kind: 'media', selected: true },
    { depth: 2, label: 'music.flac', kind: 'media' },
    { depth: 2, label: 'clip.mkv', kind: 'media' },
    { depth: 2, label: 'voice.opus', kind: 'media' },
    { depth: 2, label: 'podcast.wav', kind: 'media' },
    { depth: 1, label: 'src', kind: 'folder' },
    { depth: 1, label: 'ui', kind: 'folder' },
    { depth: 1, label: 'build.gradle.kts', kind: 'code' },
    { depth: 1, label: 'README.md', kind: 'doc' },
  ] as const

  const tabs = [
    { label: 'sintel.mp4', kind: 'media', active: true },
    { label: 'App.svelte', kind: 'code' },
  ] as const
</script>

<IdeFrame tree={tree as any} tabs={tabs as any} statusText="H.264 · AAC · 854×480">
  <VideoPlayer
    src={config.mediaUrl}
    fileName={config.fileName}
    extension={config.fileExtension}
    mediaInfo={config.mediaInfo}
  />
</IdeFrame>
