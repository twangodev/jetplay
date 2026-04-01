<script lang="ts">
  import { Volume2, VolumeX } from '@lucide/svelte'

  let {
    volume,
    muted,
    ontoggle,
    onset,
    iconClass = 'text-muted hover:text-primary',
    trackClass = 'bg-border',
    fillClass = 'bg-muted',
    sliderWidth = 'group-hover/vol:w-24',
  }: {
    volume: number
    muted: boolean
    ontoggle: () => void
    onset: (volume: number) => void
    iconClass?: string
    trackClass?: string
    fillClass?: string
    sliderWidth?: string
  } = $props()

  function handleClick(e: MouseEvent) {
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
    const pct = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
    onset(pct)
  }
</script>

<!-- svelte-ignore a11y_no_static_element_interactions -->
<div class="group/vol relative flex items-center">
  <button
    class="p-1 transition-colors bg-transparent border-none cursor-pointer {iconClass}"
    onclick={ontoggle}
  >
    {#if muted || volume === 0}
      <VolumeX size={16} />
    {:else}
      <Volume2 size={16} />
    {/if}
  </button>

  <!-- svelte-ignore a11y_click_events_have_key_events -->
  <div
    class="w-0 overflow-hidden opacity-0 {sliderWidth} group-hover/vol:opacity-100 group-hover/vol:ml-1 transition-all duration-200 h-4 flex items-center cursor-pointer"
    onclick={handleClick}
  >
    <div class="w-full h-1 {trackClass} rounded-full overflow-hidden">
      <div
        class="h-full {fillClass} rounded-full"
        style="width: {muted ? 0 : volume * 100}%"
      ></div>
    </div>
  </div>
</div>