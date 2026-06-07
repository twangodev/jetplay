<script lang="ts">
  const url = 'https://github.com/twangodev/jetplay'

  function open(e: MouseEvent) {
    e.preventDefault()
    ;(window as any).jetplayOpenLink?.(url)
  }
</script>

<button
  class="fixed bottom-2 right-2.5 text-[10px] tracking-wide z-50 transition-opacity opacity-40 hover:opacity-80 bg-transparent border-none cursor-pointer p-0 text-muted"
  onclick={open}
>
  jetplay
</button>