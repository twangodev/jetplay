<script lang="ts">
  import { formatTime } from './formatTime'

  let {
    currentTime,
    duration,
    onseek,
    showTime = true,
    trackClass = 'bg-border',
    fillClass = 'bg-primary/50',
  }: {
    currentTime: number
    duration: number
    onseek: (time: number) => void
    showTime?: boolean
    trackClass?: string
    fillClass?: string
  } = $props()

  let dragging = $state(false)

  function handleClick(e: MouseEvent) {
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
    const pct = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
    onseek(pct * duration)
  }

  function handleMouseDown(e: MouseEvent) {
    dragging = true
    handleClick(e)

    const bar = (e.currentTarget as HTMLElement)

    function onMove(ev: MouseEvent) {
      const rect = bar.getBoundingClientRect()
      const pct = Math.max(0, Math.min(1, (ev.clientX - rect.left) / rect.width))
      onseek(pct * duration)
    }

    function onUp() {
      dragging = false
      window.removeEventListener('mousemove', onMove)
      window.removeEventListener('mouseup', onUp)
    }

    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onUp)
  }
</script>

<div class="w-full">
  <!-- svelte-ignore a11y_no_static_element_interactions -->
  <div
    class="group h-5 flex items-center cursor-pointer"
    onmousedown={handleMouseDown}
  >
    <div class="w-full h-1 group-hover:h-1.5 {trackClass} rounded-full overflow-hidden transition-all">
      <div
        class="seek-fill h-full {fillClass} rounded-full"
        class:dragging
        style="width: {duration ? (currentTime / duration) * 100 : 0}%"
      ></div>
    </div>
  </div>
  {#if showTime}
    <div class="flex justify-between text-[11px] tabular-nums text-muted mt-0.5">
      <span>{formatTime(currentTime)}</span>
      <span>{formatTime(duration)}</span>
    </div>
  {/if}
</div>

<style>
  .seek-fill {
    will-change: width;
    transition: width 150ms ease-out;
  }

  .seek-fill.dragging {
    transition: none;
  }
</style>