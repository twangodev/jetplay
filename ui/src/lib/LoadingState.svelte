<script lang="ts">
  import { Loader2 } from '@lucide/svelte'

  let { fileName }: { fileName: string } = $props()
</script>

<div class="loading">
  <div class="spinner">
    <Loader2 size={28} strokeWidth={2} />
  </div>
  <div class="text">Transcoding for playback...</div>
  <div class="subtext">{fileName}</div>
</div>

<style>
  .loading {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 16px;
  }

  .spinner {
    color: var(--accent);
    animation: spin 1s linear infinite;
  }

  @keyframes spin {
    to { transform: rotate(360deg); }
  }

  .text {
    font-size: 13px;
    color: var(--text-muted);
  }

  .subtext {
    font-size: 12px;
    color: var(--text-muted);
    opacity: 0.6;
  }
</style>