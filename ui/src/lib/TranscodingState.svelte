<script lang="ts">
  import { Loader2, Info, Lightbulb } from '@lucide/svelte'
  import Branding from './Branding.svelte'

  let { fileName, progress, reason, transcodingLabel = 'Converting for playback\u2026', transcodingTip = 'Use .webm, .ogg, .opus, .wav, or .mp3 files to play instantly without conversion.' }: { fileName: string; progress: number; reason?: string; transcodingLabel?: string; transcodingTip?: string } = $props()

  const indeterminate = $derived(progress < 0)
</script>

<div class="flex-1 flex flex-col items-center justify-center gap-4 select-none p-8">
  <!-- Spinner -->
  <div class="text-muted animate-spin">
    <Loader2 size={28} strokeWidth={2} />
  </div>

  <!-- Heading -->
  <div class="text-sm text-muted">{transcodingLabel}</div>

  <!-- Progress bar -->
  <div class="w-64">
    <div class="h-1.5 bg-border rounded-full overflow-hidden">
      {#if indeterminate}
        <div class="h-full bg-primary/40 rounded-full animate-pulse w-full"></div>
      {:else}
        <div
          class="progress-fill h-full bg-primary/50 rounded-full"
          style="width: {progress}%"
        ></div>
      {/if}
    </div>

    {#if !indeterminate}
      <div class="text-xs text-muted tabular-nums text-center mt-1.5">
        {Math.round(progress)}%
      </div>
    {/if}
  </div>

  <!-- Filename -->
  <div class="text-xs text-muted opacity-60">{fileName}</div>

  <!-- Callouts -->
  <div class="flex flex-col gap-2.5 mt-2 max-w-sm w-full">
    {#if reason}
      <div class="flex items-start gap-2.5 text-xs text-muted bg-elevated border border-border rounded-lg px-3 py-2.5">
        <div class="text-muted shrink-0 mt-px">
          <Info size={14} />
        </div>
        <span>{reason}</span>
      </div>
    {/if}

    <div class="flex items-start gap-2.5 text-xs text-muted bg-elevated border border-border rounded-lg px-3 py-2.5">
      <div class="text-muted shrink-0 mt-px">
        <Lightbulb size={14} />
      </div>
      <span>{transcodingTip}</span>
    </div>
  </div>

  <Branding />
</div>

<style>
  .progress-fill {
    will-change: width;
    transition: width 600ms cubic-bezier(0.4, 0, 0.2, 1);
  }
</style>