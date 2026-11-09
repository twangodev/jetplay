<script lang="ts">
  import { onDestroy, tick } from 'svelte'
  import { Ellipsis, Repeat, Timer, Copy } from '@lucide/svelte'
  import * as DropdownMenu from '$lib/components/ui/dropdown-menu/index.js'
  import { Button } from '$lib/components/ui/button/index.js'
  import { cn } from '$lib/utils.js'
  import { formatTimestamp, parseTimestamp } from './timestamp'

  let {
    media,
    filePath,
    darkOverlay = false,
    onOpenChange,
  }: {
    media: HTMLMediaElement | null
    filePath: string
    darkOverlay?: boolean
    onOpenChange?: (open: boolean) => void
  } = $props()

  const id = $props.id()
  let menuOpen = $state(false)
  let dialogOpen = $state(false)
  let repeating = $state(false)
  let duration = $state(0)
  let timestamp = $state('')
  let error = $state('')
  let feedback = $state('')
  let destroyed = false
  let feedbackTimer: ReturnType<typeof setTimeout> | undefined
  let trigger: HTMLButtonElement
  let dialog: HTMLDialogElement
  let input: HTMLInputElement

  $effect(() => {
    const element = media
    if (!element) {
      duration = 0
      repeating = false
      return
    }
    repeating = element.loop
    const syncDuration = () => {
      duration = Number.isFinite(element.duration) && element.duration > 0 ? element.duration : 0
    }
    syncDuration()
    element.addEventListener('durationchange', syncDuration)
    element.addEventListener('loadedmetadata', syncDuration)
    element.addEventListener('emptied', syncDuration)
    return () => {
      element.removeEventListener('durationchange', syncDuration)
      element.removeEventListener('loadedmetadata', syncDuration)
      element.removeEventListener('emptied', syncDuration)
    }
  })

  $effect(() => {
    onOpenChange?.(menuOpen || dialogOpen)
  })

  onDestroy(() => {
    destroyed = true
    clearTimeout(feedbackTimer)
  })

  function setRepeat(checked: boolean) {
    if (!media) return
    media.loop = checked
    repeating = checked
  }

  async function openJump() {
    if (!media || duration <= 0) return
    timestamp = formatTimestamp(Math.min(media.currentTime, Math.floor(duration * 1000) / 1000))
    error = ''
    dialogOpen = true
    // Wait for the menu to release focus before opening the modal.
    await tick()
    if (destroyed) return
    dialog.showModal()
    input.focus()
    input.select()
  }

  function jump(event: SubmitEvent) {
    event.preventDefault()
    const time = parseTimestamp(timestamp)
    if (time === null) {
      error = 'Enter a duration, such as 1:23.450 or 1m 23.45s.'
      return
    }
    const limit = media?.duration ?? 0
    if (!Number.isFinite(limit) || limit <= 0) {
      error = 'Wait for the file to finish loading.'
      return
    }
    if (time > limit) {
      error = `Enter a time between 00:00.000 and ${formatTimestamp(Math.floor(limit * 1000) / 1000)}.`
      return
    }
    if (!media) return
    try {
      media.currentTime = time
      dialog.close()
    } catch {
      error = 'Unable to seek to that time.'
    }
  }

  function closeDialog() {
    dialogOpen = false
    trigger?.focus()
  }

  function showFeedback(message: string) {
    if (destroyed) return
    clearTimeout(feedbackTimer)
    feedback = message
    feedbackTimer = setTimeout(() => (feedback = ''), 5000)
  }

  async function copyTimestamp() {
    if (!media || duration <= 0) return
    const text = `${filePath} @ ${formatTimestamp(media.currentTime)}`
    try {
      if (window.jetplayCopyText) await window.jetplayCopyText(text)
      else await navigator.clipboard.writeText(text)
      showFeedback('Timestamp copied')
    } catch {
      showFeedback('Could not copy timestamp. Try again.')
    }
  }
</script>

<div class="relative inline-flex shrink-0 items-center">
  <DropdownMenu.Root bind:open={menuOpen}>
    <DropdownMenu.Trigger>
      {#snippet child({ props })}
        <button
          {...props}
          bind:this={trigger}
          type="button"
          aria-label="Playback actions"
          title={repeating ? 'Playback actions (repeat on)' : 'Playback actions'}
          class={cn(
            'relative inline-flex items-center justify-center rounded outline-none transition-colors focus-visible:ring-2',
            darkOverlay
              ? 'p-1 text-white/90 hover:text-white focus-visible:ring-white/60'
              : 'size-8 text-muted-foreground hover:text-foreground focus-visible:ring-ring',
          )}
        >
          <Ellipsis class="size-4" />
          {#if repeating}
            <span aria-hidden="true" class="absolute right-0.5 top-0.5 size-1 rounded-full bg-current"></span>
          {/if}
        </button>
      {/snippet}
    </DropdownMenu.Trigger>
    <DropdownMenu.Content
      align="end"
      class="min-w-48"
      onCloseAutoFocus={(event) => {
        if (dialogOpen) event.preventDefault()
      }}
    >
      <DropdownMenu.CheckboxItem checked={repeating} onCheckedChange={setRepeat} disabled={!media}>
        <Repeat class="size-4" />
        Repeat file
      </DropdownMenu.CheckboxItem>
      <DropdownMenu.Separator />
      <DropdownMenu.Item onSelect={openJump} disabled={duration <= 0}>
        <Timer class="size-4" />
        Jump to timestamp…
      </DropdownMenu.Item>
      <DropdownMenu.Item onSelect={copyTimestamp} disabled={duration <= 0}>
        <Copy class="size-4" />
        Copy timestamp
      </DropdownMenu.Item>
    </DropdownMenu.Content>
  </DropdownMenu.Root>

  <span role="status" aria-live="polite" class="sr-only">{feedback}</span>
  {#if feedback}
    <span aria-hidden="true" class="fixed bottom-4 right-4 z-50 rounded-lg border bg-popover px-3 py-2 text-xs text-popover-foreground shadow-md">
      {feedback}
    </span>
  {/if}
</div>

<dialog
  bind:this={dialog}
  aria-labelledby="{id}-title"
  aria-describedby="{id}-hint"
  onclose={closeDialog}
  class="fixed m-auto w-80 max-w-[calc(100vw-2rem)] rounded-xl border bg-popover p-4 text-popover-foreground shadow-xl backdrop:bg-black/50"
>
  <form onsubmit={jump} class="space-y-4">
    <h2 id="{id}-title" class="text-sm font-medium">Jump to timestamp</h2>
    <div class="space-y-2">
      <label for="{id}-timestamp" class="block text-xs text-muted-foreground">Timestamp</label>
      <input
        bind:this={input}
        bind:value={timestamp}
        id="{id}-timestamp"
        type="text"
        inputmode="decimal"
        autocomplete="off"
        spellcheck={false}
        aria-invalid={error ? 'true' : undefined}
        aria-describedby={error ? `${id}-hint ${id}-error` : `${id}-hint`}
        class="w-full rounded-md border bg-background px-2.5 py-2 font-mono text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
        oninput={() => (error = '')}
      />
      <p id="{id}-hint" class="text-xs text-muted-foreground">Seconds, m:ss, h:mm:ss, or 1m 23.45s</p>
      {#if error}
        <p id="{id}-error" role="alert" class="text-xs text-error">{error}</p>
      {/if}
    </div>
    <div class="flex justify-end gap-2">
      <Button variant="ghost" onclick={() => dialog.close()}>Cancel</Button>
      <Button type="submit">Jump</Button>
    </div>
  </form>
</dialog>
