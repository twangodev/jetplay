<script lang="ts">
	import type { HTMLAttributes } from "svelte/elements";
	import { cn } from "$lib/utils.js";
	import { useAudioPlayer } from "./context.svelte.js";
	import { formatTime } from "./utils.js";

	let { class: className, ...restProps }: HTMLAttributes<HTMLSpanElement> = $props();

	const player = useAudioPlayer();

	const display = $derived(
		player.duration !== undefined &&
			Number.isFinite(player.duration) &&
			!Number.isNaN(player.duration)
			? formatTime(player.duration)
			: "--:--"
	);
</script>

<span
	data-slot="audio-player-duration"
	class={cn("text-muted-foreground text-sm tabular-nums", className)}
	{...restProps}
>
	{display}
</span>
