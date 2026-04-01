<script lang="ts">
  let { src }: { src: string } = $props()
</script>

<div class="video-wrapper">
  <!-- svelte-ignore a11y_media_has_caption -->
  <video controls {src}></video>
</div>

<style>
  .video-wrapper {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #000;
    min-height: 0;
    overflow: hidden;
  }

  video {
    max-width: 100%;
    max-height: 100%;
    outline: none;
  }

  video::-webkit-media-controls-panel {
    background: linear-gradient(transparent, rgba(0, 0, 0, 0.6));
  }
</style>