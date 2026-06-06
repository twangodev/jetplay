<script lang="ts">
	import type { ComponentProps } from "svelte";
	import Check from "@lucide/svelte/icons/check";
	import Settings from "@lucide/svelte/icons/settings";
	import * as DropdownMenu from "../dropdown-menu/index.js";
	import { Button } from "../button/index.js";
	import { cn } from "$lib/utils.js";
	import { useAudioPlayer } from "./context.svelte.js";

	const PLAYBACK_SPEEDS = [0.25, 0.5, 0.75, 1, 1.25, 1.5, 1.75, 2] as const;

	type Props = ComponentProps<typeof Button> & {
		speeds?: readonly number[];
	};

	let {
		speeds = PLAYBACK_SPEEDS,
		class: className,
		variant = "ghost",
		size = "icon",
		...restProps
	}: Props = $props();

	const player = useAudioPlayer();
</script>

<DropdownMenu.Root>
	<DropdownMenu.Trigger>
		{#snippet child({ props })}
			<Button
				{variant}
				{size}
				class={cn(className)}
				aria-label="Playback speed"
				{...props}
				{...restProps}
			>
				<Settings class="size-4" />
			</Button>
		{/snippet}
	</DropdownMenu.Trigger>
	<DropdownMenu.Content align="end" class="min-w-[120px]">
		{#each speeds as speed (speed)}
			<DropdownMenu.Item
				onclick={() => player.setPlaybackRate(speed)}
				class="flex items-center justify-between"
			>
				<span class={speed === 1 ? "" : "font-mono"}>
					{speed === 1 ? "Normal" : `${speed}x`}
				</span>
				{#if player.playbackRate === speed}
					<Check class="size-4" />
				{/if}
			</DropdownMenu.Item>
		{/each}
	</DropdownMenu.Content>
</DropdownMenu.Root>
