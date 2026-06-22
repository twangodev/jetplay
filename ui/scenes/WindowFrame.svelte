<script lang="ts">
  import type { Snippet } from 'svelte'

  let { title = '', width = 760, children }: { title?: string; width?: number; children?: Snippet } = $props()
</script>

<!-- translateZ(0) makes this the containing block for the player's position:fixed Branding link -->
<div
  class="flex flex-col overflow-hidden rounded-xl border border-border shadow-2xl shadow-black/40 [transform:translateZ(0)]"
  style="width: {width}px"
>
  <div class="relative flex h-9 shrink-0 items-center bg-elevated px-4">
    <div class="flex items-center gap-2">
      <span class="size-3 rounded-full bg-traffic-red"></span>
      <span class="size-3 rounded-full bg-traffic-yellow"></span>
      <span class="size-3 rounded-full bg-traffic-green"></span>
    </div>
    {#if title}
      <span class="pointer-events-none absolute inset-x-0 text-center text-[13px] font-medium text-muted">{title}</span>
    {/if}
  </div>
  <div class="flex min-h-[460px] flex-col bg-surface">
    {@render children?.()}
  </div>
</div>
