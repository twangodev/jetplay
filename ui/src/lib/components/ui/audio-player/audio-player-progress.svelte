<script lang="ts">
	import { Slider as SliderPrimitive } from "bits-ui";
	import { cn } from "$lib/utils.js";
	import { useAudioPlayer } from "./context.svelte.js";

	type Props = Omit<
		SliderPrimitive.RootProps,
		"type" | "value" | "min" | "max" | "onValueChange" | "onValueCommit" | "children" | "child"
	> & {
		step?: number;
		onValueChange?: (value: number) => void;
	};

	let {
		class: className,
		step = 0.25,
		onValueChange: externalOnValueChange,
		onpointerdown: externalPointerDown,
		onpointerup: externalPointerUp,
		onkeydown: externalKeyDown,
		...restProps
	}: Props = $props();

	const player = useAudioPlayer();

	const disabled = $derived(
		player.duration === undefined ||
			!Number.isFinite(player.duration) ||
			Number.isNaN(player.duration)
	);
	const max = $derived(disabled ? 0 : (player.duration as number));
	const currentValue = $derived(Math.min(player.time, max));

	let wasPlaying = false;
	let userInteracting = false;
</script>

<SliderPrimitive.Root
	type="single"
	data-slot="audio-player-progress"
	min={0}
	{max}
	{step}
	{disabled}
	value={currentValue}
	onValueChange={(v) => {
		if (!userInteracting) return;
		player.seek(v);
		externalOnValueChange?.(v);
	}}
	onpointerdown={(e) => {
		userInteracting = true;
		wasPlaying = player.isPlaying;
		void player.pause();
		externalPointerDown?.(e);
	}}
	onpointerup={(e) => {
		userInteracting = false;
		if (wasPlaying) void player.play();
		externalPointerUp?.(e);
	}}
	onkeydown={(e) => {
		if (e.key === " ") {
			e.preventDefault();
			if (player.isPlaying) void player.pause();
			else void player.play();
		} else {
			userInteracting = true;
			queueMicrotask(() => {
				userInteracting = false;
			});
		}
		externalKeyDown?.(e);
	}}
	class={cn(
		"group/player relative flex h-4 touch-none items-center select-none data-disabled:opacity-50 data-vertical:h-full data-vertical:min-h-44 data-vertical:w-auto data-vertical:flex-col",
		className
	)}
	{...restProps}
>
	{#snippet children({ thumbItems })}
		<span
			data-slot="audio-player-progress-track"
			class="bg-muted relative h-[4px] w-full grow overflow-hidden rounded-full"
		>
			<SliderPrimitive.Range
				data-slot="audio-player-progress-range"
				class="bg-primary absolute h-full"
			/>
		</span>
		{#each thumbItems as thumb (thumb.index)}
			<SliderPrimitive.Thumb
				index={thumb.index}
				data-slot="audio-player-progress-thumb"
				class="relative flex h-0 w-0 items-center justify-center opacity-0 group-hover/player:opacity-100 focus-visible:opacity-100 focus-visible:outline-none disabled:pointer-events-none disabled:opacity-50"
			>
				<div class="bg-foreground absolute size-3 rounded-full"></div>
			</SliderPrimitive.Thumb>
		{/each}
	{/snippet}
</SliderPrimitive.Root>
