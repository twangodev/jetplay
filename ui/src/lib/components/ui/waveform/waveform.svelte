<script lang="ts">
	import type { HTMLAttributes } from "svelte/elements";
	import { cn } from "$lib/utils.js";
	import { getComputedBarColor, heightToCssSize } from "./utils.js";

	export type WaveformProps = HTMLAttributes<HTMLDivElement> & {
		/**
		 * Array of normalized bar values in `[0, 1]`. The component samples from
		 * this array to fill the available width.
		 * @default []
		 */
		data?: number[];
		/**
		 * Width of each bar in pixels.
		 * @default 4
		 */
		barWidth?: number;
		/**
		 * Minimum bar height in pixels. Bars are drawn at least this tall even
		 * when their value is near zero.
		 * @default 4
		 */
		barHeight?: number;
		/**
		 * Gap between bars in pixels.
		 * @default 2
		 */
		barGap?: number;
		/**
		 * Corner radius applied to each bar. Set to `0` for square bars.
		 * @default 2
		 */
		barRadius?: number;
		/**
		 * Custom bar color. Falls back to the canvas's computed `--foreground`
		 * CSS variable when unset.
		 */
		barColor?: string;
		/**
		 * Fade the left and right edges of the waveform via a destination-out
		 * gradient mask.
		 * @default true
		 */
		fadeEdges?: boolean;
		/**
		 * Width of the edge fade region in pixels.
		 * @default 24
		 */
		fadeWidth?: number;
		/**
		 * Height of the waveform container. Numbers are treated as pixels;
		 * strings are passed through as a CSS length.
		 * @default 128
		 */
		height?: string | number;
		/**
		 * Marks the waveform as actively capturing or rendering audio. Rendered
		 * as `data-active` on the root element for CSS styling hooks.
		 */
		active?: boolean;
		/** Called when a bar is clicked with the data index and its value. */
		onBarClick?: (index: number, value: number) => void;
	};

	let {
		data = [],
		barWidth = 4,
		barHeight: baseBarHeight = 4,
		barGap = 2,
		barRadius = 2,
		barColor,
		fadeEdges = true,
		fadeWidth = 24,
		height = 128,
		active,
		onBarClick,
		class: className,
		...restProps
	}: WaveformProps = $props();

	let canvasEl: HTMLCanvasElement | null = $state(null);
	let containerEl: HTMLDivElement | null = $state(null);

	const heightStyle = $derived(heightToCssSize(height));

	$effect(() => {
		const canvas = canvasEl;
		const container = containerEl;
		if (!canvas || !container) return;

		// Reactive dependency reads — re-runs the effect when any of these change.
		const _data = data;
		const _barWidth = barWidth;
		const _baseBarHeight = baseBarHeight;
		const _barGap = barGap;
		const _barRadius = barRadius;
		const _barColor = barColor;
		const _fadeEdges = fadeEdges;
		const _fadeWidth = fadeWidth;

		const renderWaveform = () => {
			const ctx = canvas.getContext("2d");
			if (!ctx) return;

			const rect = canvas.getBoundingClientRect();
			ctx.clearRect(0, 0, rect.width, rect.height);

			const computedBarColor = getComputedBarColor(canvas, _barColor);

			const barCount = Math.floor(rect.width / (_barWidth + _barGap));
			const centerY = rect.height / 2;

			for (let i = 0; i < barCount; i++) {
				const dataIndex = Math.floor((i / barCount) * _data.length);
				const value = _data[dataIndex] || 0;
				const barHeightPx = Math.max(_baseBarHeight, value * rect.height * 0.8);
				const x = i * (_barWidth + _barGap);
				const y = centerY - barHeightPx / 2;

				ctx.fillStyle = computedBarColor;
				ctx.globalAlpha = 0.3 + value * 0.7;

				if (_barRadius > 0) {
					ctx.beginPath();
					ctx.roundRect(x, y, _barWidth, barHeightPx, _barRadius);
					ctx.fill();
				} else {
					ctx.fillRect(x, y, _barWidth, barHeightPx);
				}
			}

			if (_fadeEdges && _fadeWidth > 0 && rect.width > 0) {
				const gradient = ctx.createLinearGradient(0, 0, rect.width, 0);
				const fadePercent = Math.min(0.2, _fadeWidth / rect.width);

				gradient.addColorStop(0, "rgba(255,255,255,1)");
				gradient.addColorStop(fadePercent, "rgba(255,255,255,0)");
				gradient.addColorStop(1 - fadePercent, "rgba(255,255,255,0)");
				gradient.addColorStop(1, "rgba(255,255,255,1)");

				ctx.globalCompositeOperation = "destination-out";
				ctx.fillStyle = gradient;
				ctx.fillRect(0, 0, rect.width, rect.height);
				ctx.globalCompositeOperation = "source-over";
			}

			ctx.globalAlpha = 1;
		};

		const resizeObserver = new ResizeObserver(() => {
			const rect = container.getBoundingClientRect();
			const dpr = window.devicePixelRatio || 1;

			canvas.width = rect.width * dpr;
			canvas.height = rect.height * dpr;
			canvas.style.width = `${rect.width}px`;
			canvas.style.height = `${rect.height}px`;

			const ctx = canvas.getContext("2d");
			if (ctx) {
				ctx.scale(dpr, dpr);
				renderWaveform();
			}
		});

		resizeObserver.observe(container);
		renderWaveform();

		return () => resizeObserver.disconnect();
	});

	function handleClick(e: MouseEvent) {
		if (!onBarClick || !canvasEl) return;

		const rect = canvasEl.getBoundingClientRect();
		const x = e.clientX - rect.left;
		const barIndex = Math.floor(x / (barWidth + barGap));
		const dataIndex = Math.floor(
			(barIndex * data.length) / Math.floor(rect.width / (barWidth + barGap))
		);

		if (dataIndex >= 0 && dataIndex < data.length) {
			onBarClick(dataIndex, data[dataIndex]);
		}
	}
</script>

<div
	bind:this={containerEl}
	data-slot="waveform"
	data-active={active ? "" : undefined}
	class={cn("relative", className)}
	style:height={heightStyle}
	{...restProps}
>
	<canvas bind:this={canvasEl} class="block h-full w-full" onclick={handleClick}></canvas>
</div>
