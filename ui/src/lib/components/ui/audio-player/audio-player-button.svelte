<script lang="ts">
	import type { ComponentProps } from "svelte";
	import Pause from "@lucide/svelte/icons/pause";
	import Play from "@lucide/svelte/icons/play";
	import { Button } from "../button/index.js";
	import { cn } from "$lib/utils.js";
	import { useAudioPlayer, type AudioPlayerItem } from "./context.svelte.js";

	type Props = ComponentProps<typeof Button> & {
		item?: AudioPlayerItem;
		onclick?: (e: MouseEvent) => void;
	};

	let {
		item,
		class: className,
		onclick: externalOnClick,
		children,
		...restProps
	}: Props = $props();

	const player = useAudioPlayer();

	const playing = $derived(
		item ? player.isItemActive(item.id) && player.isPlaying : player.isPlaying
	);
	const loading = $derived(
		item
			? player.isItemActive(item.id) && player.isBuffering && player.isPlaying
			: player.isBuffering && player.isPlaying
	);
</script>

<Button
	type="button"
	aria-label={playing ? "Pause" : "Play"}
	class={cn("relative", className)}
	onclick={(e) => {
		const shouldPlay = !playing;
		if (shouldPlay) {
			if (item) {
				void player.play(item);
			} else {
				void player.play();
			}
		} else {
			void player.pause();
		}
		externalOnClick?.(e);
	}}
	{...restProps}
>
	{#if playing}
		<Pause class={cn("size-4", loading && "opacity-0")} aria-hidden="true" />
	{:else}
		<Play class={cn("size-4", loading && "opacity-0")} aria-hidden="true" />
	{/if}
	{#if loading}
		<div
			class="absolute inset-0 flex items-center justify-center rounded-[inherit] backdrop-blur-xs"
		>
			<div
				class="border-muted border-t-foreground size-3.5 animate-spin rounded-full border-2"
				role="status"
				aria-label="Loading"
			>
				<span class="sr-only">Loading...</span>
			</div>
		</div>
	{/if}
	{@render children?.()}
</Button>
