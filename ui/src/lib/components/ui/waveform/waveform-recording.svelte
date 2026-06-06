<script lang="ts">
	import { cn } from "$lib/utils.js";
	import { getComputedBarColor, heightToCssSize } from "./utils.js";
	import type { WaveformProps } from "./waveform.svelte";

	export type RecordingWaveformProps = Omit<WaveformProps, "data" | "onBarClick"> & {
		recording?: boolean;
		fftSize?: number;
		smoothingTimeConstant?: number;
		sensitivity?: number;
		onError?: (error: Error) => void;
		onRecordingComplete?: (data: number[]) => void;
		updateRate?: number;
		showHandle?: boolean;
	};

	let {
		recording = false,
		fftSize = 256,
		smoothingTimeConstant = 0.8,
		sensitivity = 1,
		onError,
		onRecordingComplete,
		updateRate = 50,
		showHandle = true,
		barWidth = 3,
		barHeight: baseBarHeight = 4,
		barGap = 1,
		barRadius = 1,
		barColor,
		height = 128,
		class: className,
		...restProps
	}: RecordingWaveformProps = $props();

	let recordedData: number[] = $state([]);
	let viewPosition = $state(1);
	let isRecordingComplete = $state(false);

	let canvasEl: HTMLCanvasElement | null = $state(null);
	let containerEl: HTMLDivElement | null = $state(null);

	// Non-reactive refs.
	let recordingDataRef: number[] = [];
	let analyserRef: AnalyserNode | null = null;
	let audioContextRef: AudioContext | null = null;
	let streamRef: MediaStream | null = null;
	let animationRef: number | null = null;
	let lastUpdateRef = 0;

	const heightStyle = $derived(heightToCssSize(height));

	// ResizeObserver — runs once on mount.
	$effect(() => {
		const canvas = canvasEl;
		const container = containerEl;
		if (!canvas || !container) return;

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
		});

		resizeObserver.observe(container);
		return () => resizeObserver.disconnect();
	});

	// Mic setup — keyed on [recording, fftSize, smoothingTimeConstant, onError, onRecordingComplete].
	$effect(() => {
		const _recording = recording;
		const _fftSize = fftSize;
		const _smoothingTimeConstant = smoothingTimeConstant;
		const _onError = onError;
		const _onRecordingComplete = onRecordingComplete;

		if (!_recording) {
			if (streamRef) {
				streamRef.getTracks().forEach((track) => track.stop());
			}
			if (audioContextRef && audioContextRef.state !== "closed") {
				audioContextRef.close();
			}

			if (recordingDataRef.length > 0) {
				recordedData = [...recordingDataRef];
				isRecordingComplete = true;
				_onRecordingComplete?.(recordingDataRef);
			}
			return;
		}

		isRecordingComplete = false;
		recordingDataRef = [];
		recordedData = [];
		viewPosition = 1;

		let cancelled = false;

		const setupMicrophone = async () => {
			try {
				const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
				if (cancelled) {
					stream.getTracks().forEach((track) => track.stop());
					return;
				}
				streamRef = stream;

				const AudioContextCtor =
					window.AudioContext ||
					(window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
				const audioContext = new AudioContextCtor();
				const analyser = audioContext.createAnalyser();
				analyser.fftSize = _fftSize;
				analyser.smoothingTimeConstant = _smoothingTimeConstant;

				const source = audioContext.createMediaStreamSource(stream);
				source.connect(analyser);

				audioContextRef = audioContext;
				analyserRef = analyser;
			} catch (error) {
				_onError?.(error as Error);
			}
		};

		setupMicrophone();

		return () => {
			cancelled = true;
			if (streamRef) {
				streamRef.getTracks().forEach((track) => track.stop());
				streamRef = null;
			}
			if (audioContextRef && audioContextRef.state !== "closed") {
				audioContextRef.close();
				audioContextRef = null;
			}
			analyserRef = null;
		};
	});

	// Render RAF loop — re-runs on many deps.
	$effect(() => {
		const canvas = canvasEl;
		if (!canvas) return;

		const ctx = canvas.getContext("2d");
		if (!ctx) return;

		const _recording = recording;
		const _recordedData = recordedData;
		const _viewPosition = viewPosition;
		const _isRecordingComplete = isRecordingComplete;
		const _sensitivity = sensitivity;
		const _updateRate = updateRate;
		const _showHandle = showHandle;
		const _barWidth = barWidth;
		const _baseBarHeight = baseBarHeight;
		const _barGap = barGap;
		const _barRadius = barRadius;
		const _barColor = barColor;

		const animate = (currentTime: number) => {
			if (_recording && currentTime - lastUpdateRef > _updateRate) {
				lastUpdateRef = currentTime;

				if (analyserRef) {
					const dataArray = new Uint8Array(analyserRef.frequencyBinCount);
					analyserRef.getByteFrequencyData(dataArray);

					let sum = 0;
					for (let i = 0; i < dataArray.length; i++) {
						sum += dataArray[i];
					}
					const average = (sum / dataArray.length / 255) * _sensitivity;

					recordingDataRef.push(Math.min(1, Math.max(0.05, average)));
				}
			}

			const rect = canvas.getBoundingClientRect();
			ctx.clearRect(0, 0, rect.width, rect.height);

			const computedBarColor = getComputedBarColor(canvas, _barColor);

			const dataToRender = _recording ? recordingDataRef : _recordedData;

			if (dataToRender.length > 0) {
				const step = _barWidth + _barGap;
				const barsVisible = Math.floor(rect.width / step);
				const centerY = rect.height / 2;

				let startIndex = 0;
				if (!_recording && _isRecordingComplete) {
					const totalBars = dataToRender.length;
					if (totalBars > barsVisible) {
						startIndex = Math.floor((totalBars - barsVisible) * _viewPosition);
					}
				} else if (_recording) {
					startIndex = Math.max(0, dataToRender.length - barsVisible);
				}

				for (let i = 0; i < barsVisible && startIndex + i < dataToRender.length; i++) {
					const value = dataToRender[startIndex + i] || 0.1;
					const x = i * step;
					const barHeightPx = Math.max(_baseBarHeight, value * rect.height * 0.7);
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

				if (!_recording && _isRecordingComplete && _showHandle) {
					const indicatorX = rect.width * _viewPosition;

					ctx.strokeStyle = computedBarColor;
					ctx.globalAlpha = 0.5;
					ctx.lineWidth = 2;
					ctx.beginPath();
					ctx.moveTo(indicatorX, 0);
					ctx.lineTo(indicatorX, rect.height);
					ctx.stroke();
					ctx.fillStyle = computedBarColor;
					ctx.globalAlpha = 1;
					ctx.beginPath();
					ctx.arc(indicatorX, centerY, 6, 0, Math.PI * 2);
					ctx.fill();
				}
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

	function handleScrub(clientX: number) {
		if (!containerEl || recording || !isRecordingComplete) return;
		const rect = containerEl.getBoundingClientRect();
		const x = Math.max(0, Math.min(clientX - rect.left, rect.width));
		viewPosition = x / rect.width;
	}

	function handlePointerDown(event: PointerEvent) {
		if (recording || !isRecordingComplete) return;
		event.preventDefault();
		handleScrub(event.clientX);

		const handleMove = (moveEvent: PointerEvent) => {
			handleScrub(moveEvent.clientX);
		};

		const handleUp = () => {
			window.removeEventListener("pointermove", handleMove);
			window.removeEventListener("pointerup", handleUp);
		};

		window.addEventListener("pointermove", handleMove);
		window.addEventListener("pointerup", handleUp, { once: true });
	}
</script>

<!-- svelte-ignore a11y_no_noninteractive_tabindex -->
<div
	bind:this={containerEl}
	data-slot="recording-waveform"
	aria-label={isRecordingComplete && !recording ? "Drag to scrub through recording" : undefined}
	aria-valuenow={isRecordingComplete && !recording ? viewPosition * 100 : undefined}
	aria-valuemin={isRecordingComplete && !recording ? 0 : undefined}
	aria-valuemax={isRecordingComplete && !recording ? 100 : undefined}
	role={isRecordingComplete && !recording ? "slider" : undefined}
	tabindex={isRecordingComplete && !recording ? 0 : undefined}
	class={cn(
		"relative flex items-center",
		isRecordingComplete && !recording && "cursor-pointer",
		className
	)}
	style:height={heightStyle}
	onpointerdown={handlePointerDown}
	{...restProps}
>
	<canvas bind:this={canvasEl} class="block h-full w-full"></canvas>
</div>
