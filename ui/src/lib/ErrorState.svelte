<script lang="ts">
  import { TriangleAlert } from '@lucide/svelte'

  let { message }: { message: string } = $props()
</script>

<div class="flex-1 flex flex-col items-center justify-center gap-2.5">
  <div class="text-error opacity-80 mb-1">
    <TriangleAlert size={32} strokeWidth={1.5} />
  </div>
  <div class="text-sm font-medium text-error">Unable to play this file</div>
  <div class="text-xs text-muted max-w-[400px] text-center break-words leading-relaxed">
    {message}
  </div>
</div>
