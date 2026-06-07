<script lang="ts">
	import { cn } from "$lib/utils.js";
	import { getComputedBarColor, heightToCssSize } from "./utils.js";
	import type { WaveformProps } from "./waveform.svelte";

	export type ScrollingWaveformProps = Omit<WaveformProps, "data" | "onBarClick"> & {
		speed?: number;
		barCount?: number;
		data?: number[];
	};

	let {
		speed = 50,
		barCount = 60,
		barWidth = 4,
		barHeight: baseBarHeight = 4,
		barGap = 2,
		barRadius = 2,
		barColor,
		fadeEdges = true,
		fadeWidth = 24,
		height = 128,
		data,
		class: className,
		...restProps
	}: ScrollingWaveformProps = $props();

	let canvasEl: HTMLCanvasElement | null = $state(null);
	let containerEl: HTMLDivElement | null = $state(null);

	// Non-reactive mutable refs — plain `let`, not $state.
	let barsRef: Array<{ x: number; height: number }> = [];
	let animationRef: number | null = null;
	let lastTimeRef = 0;
	const seedRef = Math.random();
	let dataIndexRef = 0;

	const heightStyle = $derived(heightToCssSize(height));

	// ResizeObserver — re-runs when barWidth/barGap change (React deps [barWidth, barGap]).
	$effect(() => {
		const canvas = canvasEl;
		const container = containerEl;
		if (!canvas || !container) return;

		const _barWidth = barWidth;
		const _barGap = barGap;

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
			}

			if (barsRef.length === 0) {
				const step = _barWidth + _barGap;
				let currentX = rect.width;
				let index = 0;
				const seeded = (i: number) => {
					const x = Math.sin(seedRef * 10000 + i) * 10000;
					return x - Math.floor(x);
				};
				while (currentX > -step) {
					barsRef.push({
						x: currentX,
						height: 0.2 + seeded(index++) * 0.6,
					});
					currentX -= step;
				}
			}
		});

		resizeObserver.observe(container);
		return () => resizeObserver.disconnect();
	});

	// RAF loop — re-runs when any render prop changes (matches React deps).
	$effect(() => {
		const canvas = canvasEl;
		if (!canvas) return;

		const ctx = canvas.getContext("2d");
		if (!ctx) return;

		// Reactive dependency reads.
		const _speed = speed;
		const _barCount = barCount;
		const _barWidth = barWidth;
		const _baseBarHeight = baseBarHeight;
		const _barGap = barGap;
		const _barRadius = barRadius;
		const _barColor = barColor;
		const _fadeEdges = fadeEdges;
		const _fadeWidth = fadeWidth;
		const _data = data;

		const animate = (currentTime: number) => {
			const deltaTime = lastTimeRef ? (currentTime - lastTimeRef) / 1000 : 0;
			lastTimeRef = currentTime;

			const rect = canvas.getBoundingClientRect();
			ctx.clearRect(0, 0, rect.width, rect.height);

			const computedBarColor = getComputedBarColor(canvas, _barColor);

			const step = _barWidth + _barGap;
			for (let i = 0; i < barsRef.length; i++) {
				barsRef[i].x -= _speed * deltaTime;
			}

			barsRef = barsRef.filter((bar) => bar.x + _barWidth > -step);

			while (barsRef.length === 0 || barsRef[barsRef.length - 1].x < rect.width) {
				const lastBar = barsRef[barsRef.length - 1];
				const nextX = lastBar ? lastBar.x + step : rect.width;

				let newHeight: number;
				if (_data && _data.length > 0) {
					newHeight = _data[dataIndexRef % _data.length] || 0.1;
					dataIndexRef = (dataIndexRef + 1) % _data.length;
				} else {
					const time = Date.now() / 1000;
					const uniqueIndex = barsRef.length + time * 0.01;
					const seeded = (idx: number) => {
						const x = Math.sin(seedRef * 10000 + idx * 137.5) * 10000;
						return x - Math.floor(x);
					};
					const wave1 = Math.sin(uniqueIndex * 0.1) * 0.2;
					const wave2 = Math.cos(uniqueIndex * 0.05) * 0.15;
					const randomComponent = seeded(uniqueIndex) * 0.4;
					newHeight = Math.max(0.1, Math.min(0.9, 0.3 + wave1 + wave2 + randomComponent));
				}

				barsRef.push({
					x: nextX,
					height: newHeight,
				});
				if (barsRef.length > _barCount * 2) break;
			}

			const centerY = rect.height / 2;
			for (const bar of barsRef) {
				if (bar.x < rect.width && bar.x + _barWidth > 0) {
					const barHeightPx = Math.max(_baseBarHeight, bar.height * rect.height * 0.6);
					const y = centerY - barHeightPx / 2;

					ctx.fillStyle = computedBarColor;
					ctx.globalAlpha = 0.3 + bar.height * 0.7;

					if (_barRadius > 0) {
						ctx.beginPath();
						ctx.roundRect(bar.x, y, _barWidth, barHeightPx, _barRadius);
						ctx.fill();
					} else {
						ctx.fillRect(bar.x, y, _barWidth, barHeightPx);
					}
				}
			}

			if (_fadeEdges && _fadeWidth > 0) {
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

			animationRef = requestAnimationFrame(animate);
		};

		animationRef = requestAnimationFrame(animate);

		return () => {
			if (animationRef !== null) {
				cancelAnimationFrame(animationRef);
				animationRef = null;
			}
		};
	});
</script>

<div
	bind:this={containerEl}
	data-slot="scrolling-waveform"
	class={cn("relative flex items-center", className)}
	style:height={heightStyle}
	{...restProps}
>
	<canvas bind:this={canvasEl} class="block h-full w-full"></canvas>
</div>
