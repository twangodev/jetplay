<script lang="ts">
	import { cn } from "$lib/utils.js";
	import { heightToCssSize, seededRandom } from "./utils.js";
	import Waveform, { type WaveformProps } from "./waveform.svelte";

	export type AudioScrubberProps = WaveformProps & {
		currentTime?: number;
		duration?: number;
		onSeek?: (time: number) => void;
		showHandle?: boolean;
	};

	let {
		data = [],
		currentTime = 0,
		duration = 100,
		onSeek,
		showHandle = true,
		barWidth = 3,
		barHeight,
		barGap = 1,
		barRadius = 1,
		barColor,
		height = 128,
		class: className,
		...restProps
	}: AudioScrubberProps = $props();

	let containerEl: HTMLDivElement | null = $state(null);
	let isDragging = $state(false);
	let localProgress = $state(0);

	// Stable fallback seed per component instance — diverges from React's per-render
	// `Math.random()` (which is unstable anyway). Deterministic filler bars when no
	// data is provided.
	const fallbackSeed = Math.random();

	const waveformData = $derived(
		data.length > 0
			? data
			: Array.from({ length: 100 }, (_, i) => 0.2 + seededRandom(fallbackSeed * 10000 + i) * 0.6)
	);

	const heightStyle = $derived(heightToCssSize(height));

	$effect(() => {
		if (!isDragging && duration > 0) {
			localProgress = currentTime / duration;
		}
	});

	function handleScrub(clientX: number) {
		if (!containerEl) return;
		const rect = containerEl.getBoundingClientRect();
		const x = Math.max(0, Math.min(clientX - rect.left, rect.width));
		const progress = x / rect.width;
		localProgress = progress;
		onSeek?.(progress * duration);
	}

	function handlePointerDown(event: PointerEvent) {
		event.preventDefault();
		isDragging = true;
		handleScrub(event.clientX);

		const handleMove = (moveEvent: PointerEvent) => {
			handleScrub(moveEvent.clientX);
		};

		const handleUp = () => {
			isDragging = false;
			window.removeEventListener("pointermove", handleMove);
			window.removeEventListener("pointerup", handleUp);
		};

		window.addEventListener("pointermove", handleMove);
		window.addEventListener("pointerup", handleUp, { once: true });
	}
</script>

<div
	bind:this={containerEl}
	data-slot="audio-scrubber"
	aria-label="Audio waveform scrubber"
	aria-valuemax={duration}
	aria-valuemin={0}
	aria-valuenow={currentTime}
	role="slider"
	tabindex={0}
	class={cn("relative cursor-pointer select-none", className)}
	style:height={heightStyle}
	onpointerdown={handlePointerDown}
	{...restProps}
>
	<Waveform
		{barColor}
		{barGap}
		{barRadius}
		{barWidth}
		{barHeight}
		data={waveformData}
		fadeEdges={false}
	/>

	<div
		class="bg-primary/20 pointer-events-none absolute inset-y-0 left-0"
		style:width="{localProgress * 100}%"
	></div>

	<div
		class="bg-primary pointer-events-none absolute top-0 bottom-0 w-0.5"
		style:left="{localProgress * 100}%"
	></div>

	{#if showHandle}
		<div
			class="border-background bg-primary pointer-events-none absolute top-1/2 h-4 w-4 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 shadow-lg transition-transform hover:scale-110"
			style:left="{localProgress * 100}%"
		></div>
	{/if}
</div>
