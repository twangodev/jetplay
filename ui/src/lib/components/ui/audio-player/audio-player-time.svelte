<script lang="ts">
	import type { HTMLAttributes } from "svelte/elements";
	import { cn } from "$lib/utils.js";
	import { useAudioPlayer } from "./context.svelte.js";
	import { formatTime } from "./utils.js";

	let { class: className, ...restProps }: HTMLAttributes<HTMLSpanElement> = $props();

	const player = useAudioPlayer();
</script>

<span
	data-slot="audio-player-time"
	class={cn("text-muted-foreground text-sm tabular-nums", className)}
	{...restProps}
>
	{formatTime(player.time)}
</span>
