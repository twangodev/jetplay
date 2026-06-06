<script lang="ts">
	import type { HTMLAttributes } from "svelte/elements";
	import { Button } from "../button/index.js";
	import { cn } from "$lib/utils.js";
	import { useAudioPlayer } from "./context.svelte.js";

	type Props = Omit<HTMLAttributes<HTMLDivElement>, "children"> & {
		speeds?: readonly number[];
	};

	let { speeds = [0.5, 1, 1.5, 2], class: className, ...restProps }: Props = $props();

	const player = useAudioPlayer();
</script>

<div
	data-slot="audio-player-speed-button-group"
	class={cn("flex items-center gap-1", className)}
	role="group"
	aria-label="Playback speed controls"
	{...restProps}
>
	{#each speeds as speed (speed)}
		<Button
			variant={player.playbackRate === speed ? "default" : "outline"}
			size="sm"
			onclick={() => player.setPlaybackRate(speed)}
			class="min-w-[50px] font-mono text-xs"
		>
			{speed}x
		</Button>
	{/each}
</div>
