<script lang="ts">
  import { Music } from '@lucide/svelte'

  let { src, fileName, extension }: { src: string; fileName: string; extension: string } = $props()
</script>

<div class="audio-display">
  <div class="artwork">
    <Music size={40} strokeWidth={1.5} />
  </div>

  <div class="meta">
    <div class="filename">{fileName}</div>
    {#if extension}
      <span class="badge">{extension}</span>
    {/if}
  </div>

  <div class="controls">
    <audio controls {src}></audio>
  </div>
</div>

<style>
  .audio-display {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 20px;
    user-select: none;
    padding: 32px;
  }

  .artwork {
    width: 88px;
    height: 88px;
    border-radius: 18px;
    background: var(--bg-elevated);
    border: 1px solid var(--border);
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--accent);
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
  }

  .meta {
    text-align: center;
  }

  .filename {
    font-size: 15px;
    font-weight: 500;
    color: var(--text);
    max-width: 400px;
    word-break: break-word;
    line-height: 1.4;
  }

  .badge {
    display: inline-block;
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0.5px;
    text-transform: uppercase;
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 12%, transparent);
    padding: 2px 8px;
    border-radius: 4px;
    margin-top: 6px;
  }

  .controls {
    width: 100%;
    max-width: 480px;
  }

  audio {
    width: 100%;
    height: 40px;
    outline: none;
    border-radius: 8px;
  }
</style>