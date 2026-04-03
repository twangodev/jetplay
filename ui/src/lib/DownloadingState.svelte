<script lang="ts">
  import { Loader2, Info } from '@lucide/svelte'
  import Branding from './Branding.svelte'

  let { fileName, progress, reason, downloadingLabel = 'Downloading\u2026' }: { fileName: string; progress: number; reason?: string; downloadingLabel?: string } = $props()

  const indeterminate = $derived(progress < 0)
</script>

<div class="flex-1 flex flex-col items-center justify-center gap-4 select-none p-8">
  <div class="text-muted animate-spin">
    <Loader2 size={28} strokeWidth={2} />
  </div>

  <div class="text-sm text-muted">{downloadingLabel}</div>

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

  <div class="text-xs text-muted opacity-60">{fileName}</div>

  {#if reason}
    <div class="flex items-start gap-2.5 text-xs text-muted bg-elevated border border-border rounded-lg px-3 py-2.5 max-w-sm w-full mt-2">
      <div class="text-muted shrink-0 mt-px">
        <Info size={14} />
      </div>
      <span>{reason}</span>
    </div>
  {/if}

  <Branding />
</div>

<style>
  .progress-fill {
    will-change: width;
    transition: width 600ms cubic-bezier(0.4, 0, 0.2, 1);
  }
</style>
