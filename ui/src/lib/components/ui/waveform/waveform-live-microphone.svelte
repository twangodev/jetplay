<script lang="ts">
	import { cn } from "$lib/utils.js";
	import { getComputedBarColor, heightToCssSize } from "./utils.js";
	import type { ScrollingWaveformProps } from "./waveform-scrolling.svelte";

	export type LiveMicrophoneWaveformProps = Omit<ScrollingWaveformProps, "barCount"> & {
		active?: boolean;
		fftSize?: number;
		smoothingTimeConstant?: number;
		sensitivity?: number;
		onError?: (error: Error) => void;
		historySize?: number;
		updateRate?: number;
		savedHistoryRef?: { current: number[] };
		dragOffset?: number;
		setDragOffset?: (offset: number) => void;
		enableAudioPlayback?: boolean;
		playbackRate?: number;
	};

	let {
		active = false,
		fftSize = 256,
		smoothingTimeConstant = 0.8,
		sensitivity = 1,
		onError,
		historySize = 150,
		updateRate = 50,
		barWidth = 3,
		barHeight: baseBarHeight = 4,
		barGap = 1,
		barRadius = 1,
		barColor,
		fadeEdges = true,
		fadeWidth = 24,
		height = 128,
		class: className,
		savedHistoryRef,
		dragOffset: externalDragOffset,
		setDragOffset: externalSetDragOffset,
		enableAudioPlayback = true,
		playbackRate = 1,
		...restProps
	}: LiveMicrophoneWaveformProps = $props();

	// Reactive state
	let internalDragOffset = $state(0);
	let playbackPosition = $state<number | null>(null);

	// Element bindings
	let canvasEl: HTMLCanvasElement | null = $state(null);
	let containerEl: HTMLDivElement | null = $state(null);

	// Non-reactive refs — plain objects/lets, not $state.
	const internalHistoryRef = { current: [] as number[] };
	let analyserRef: AnalyserNode | null = null;
	let audioContextRef: AudioContext | null = null;
	let streamRef: MediaStream | null = null;
	let animationRef: number | null = null;
	let lastUpdateRef = 0;
	let dragStartXRef = 0;
	let dragStartOffsetRef = 0;
	let playbackStartTimeRef = 0;

	let mediaRecorderRef: MediaRecorder | null = null;
	let audioChunksRef: Blob[] = [];
	let audioBufferRef: AudioBuffer | null = null;
	let sourceNodeRef: AudioBufferSourceNode | null = null;
	let scrubSourceRef: AudioBufferSourceNode | null = null;

	// Derived ref aliases — match React's `savedHistoryRef || internalHistoryRef` pattern.
	const historyRef = $derived(savedHistoryRef ?? internalHistoryRef);
	const dragOffset = $derived(externalDragOffset ?? internalDragOffset);
	const setDragOffset = (offset: number) => {
		if (externalSetDragOffset) externalSetDragOffset(offset);
		else internalDragOffset = offset;
	};

	const heightStyle = $derived(heightToCssSize(height));

	// --- Helpers ---

	async function processAudioBlob(blob: Blob) {
		try {
			const arrayBuffer = await blob.arrayBuffer();
			if (audioContextRef) {
				const audioBuffer = await audioContextRef.decodeAudioData(arrayBuffer);
				audioBufferRef = audioBuffer;
			}
		} catch (error) {
			console.error("Error processing audio:", error);
		}
	}

	function playScrubSound(position: number, direction: number) {
		if (!enableAudioPlayback || !audioBufferRef || !audioContextRef) return;

		if (scrubSourceRef) {
			try {
				scrubSourceRef.stop();
			} catch {
				// ignore — source may already be stopped
			}
		}

		const source = audioContextRef.createBufferSource();
		source.buffer = audioBufferRef;

		const speed = Math.abs(direction);
		const rate = direction > 0 ? Math.min(3, 1 + speed * 0.1) : Math.max(-3, -1 - speed * 0.1);

		source.playbackRate.value = rate;

		const filter = audioContextRef.createBiquadFilter();
		filter.type = "lowpass";
		filter.frequency.value = Math.max(200, 2000 - speed * 100);

		source.connect(filter);
		filter.connect(audioContextRef.destination);

		const startTime = Math.max(0, Math.min(position, audioBufferRef.duration - 0.1));
		source.start(0, startTime, 0.1);
		scrubSourceRef = source;
	}

	function playFromPosition(position: number) {
		if (!enableAudioPlayback || !audioBufferRef || !audioContextRef) return;

		if (sourceNodeRef) {
			try {
				sourceNodeRef.stop();
			} catch {
				// ignore — source may already be stopped
			}
		}

		const source = audioContextRef.createBufferSource();
		source.buffer = audioBufferRef;
		source.playbackRate.value = playbackRate;
		source.connect(audioContextRef.destination);

		const startTime = Math.max(0, Math.min(position, audioBufferRef.duration));
		source.start(0, startTime);
		sourceNodeRef = source;

		playbackStartTimeRef = audioContextRef.currentTime - startTime;
		playbackPosition = startTime;

		source.onended = () => {
			playbackPosition = null;
		};
	}

	// --- Effects ---

	// 1. ResizeObserver — runs once on mount.
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

	// 2. Mic setup + MediaRecorder.
	$effect(() => {
		const _active = active;
		const _fftSize = fftSize;
		const _smoothingTimeConstant = smoothingTimeConstant;
		const _onError = onError;
		const _enableAudioPlayback = enableAudioPlayback;
		const _historyRef = historyRef;

		if (!_active) {
			if (mediaRecorderRef && mediaRecorderRef.state !== "inactive") {
				mediaRecorderRef.stop();
			}
			if (streamRef) {
				streamRef.getTracks().forEach((track) => track.stop());
			}
			if (_enableAudioPlayback && audioChunksRef.length > 0) {
				const audioBlob = new Blob(audioChunksRef, { type: "audio/webm" });
				processAudioBlob(audioBlob);
			}
			return;
		}

		setDragOffset(0);
		_historyRef.current = [];
		audioChunksRef = [];
		audioBufferRef = null;
		playbackPosition = null;

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

				if (_enableAudioPlayback) {
					const mediaRecorder = new MediaRecorder(stream);
					mediaRecorderRef = mediaRecorder;

					mediaRecorder.ondataavailable = (event) => {
						if (event.data.size > 0) {
							audioChunksRef.push(event.data);
						}
					};

					mediaRecorder.start(100);
				}
			} catch (error) {
				_onError?.(error as Error);
			}
		};

		setupMicrophone();

		return () => {
			cancelled = true;
			if (mediaRecorderRef && mediaRecorderRef.state !== "inactive") {
				mediaRecorderRef.stop();
			}
			if (streamRef) {
				streamRef.getTracks().forEach((track) => track.stop());
			}
			if (sourceNodeRef) {
				try {
					sourceNodeRef.stop();
				} catch {
					// ignore
				}
			}
			if (scrubSourceRef) {
				try {
					scrubSourceRef.stop();
				} catch {
					// ignore
				}
			}
		};
	});

	// 3. Playback visual sync — keyed on playbackPosition.
	$effect(() => {
		if (playbackPosition === null || !audioBufferRef) return;

		const _playbackPosition = playbackPosition;
		const _playbackRate = playbackRate;
		const _barWidth = barWidth;
		const _barGap = barGap;
		const _historyRef = historyRef;

		let animationId: number | null = null;

		const updatePlaybackVisual = () => {
			if (audioContextRef && sourceNodeRef && audioBufferRef) {
				const elapsed = audioContextRef.currentTime - playbackStartTimeRef;
				const currentPos = _playbackPosition + elapsed * _playbackRate;

				if (currentPos < audioBufferRef.duration) {
					const progressRatio = currentPos / audioBufferRef.duration;
					const currentBarIndex = Math.floor(progressRatio * _historyRef.current.length);
					const step = _barWidth + _barGap;

					const containerWidth = containerEl?.getBoundingClientRect().width || 0;
					const viewBars = Math.floor(containerWidth / step);
					const targetOffset = -(currentBarIndex - (_historyRef.current.length - viewBars)) * step;
					const clampedOffset = Math.max(
						-(_historyRef.current.length - viewBars) * step,
						Math.min(0, targetOffset)
					);

					setDragOffset(clampedOffset);
					animationId = requestAnimationFrame(updatePlaybackVisual);
				} else {
					playbackPosition = null;
					const step = _barWidth + _barGap;
					const containerWidth = containerEl?.getBoundingClientRect().width || 0;
					const viewBars = Math.floor(containerWidth / step);
					setDragOffset(-(_historyRef.current.length - viewBars) * step);
				}
			}
		};

		animationId = requestAnimationFrame(updatePlaybackVisual);

		return () => {
			if (animationId !== null) cancelAnimationFrame(animationId);
		};
	});

	// 4. Canvas render RAF loop.
	$effect(() => {
		const canvas = canvasEl;
		if (!canvas) return;

		const _active = active;
		const _sensitivity = sensitivity;
		const _updateRate = updateRate;
		const _historySize = historySize;
		const _barWidth = barWidth;
		const _baseBarHeight = baseBarHeight;
		const _barGap = barGap;
		const _barRadius = barRadius;
		const _barColor = barColor;
		const _fadeEdges = fadeEdges;
		const _fadeWidth = fadeWidth;
		const _dragOffset = dragOffset;
		const _playbackPosition = playbackPosition;
		const _historyRef = historyRef;

		if (!_active && _historyRef.current.length === 0 && _playbackPosition === null) return;

		const ctx = canvas.getContext("2d");
		if (!ctx) return;

		const animate = (currentTime: number) => {
			if (_active && currentTime - lastUpdateRef > _updateRate) {
				lastUpdateRef = currentTime;

				if (analyserRef) {
					const dataArray = new Uint8Array(analyserRef.frequencyBinCount);
					analyserRef.getByteFrequencyData(dataArray);

					let sum = 0;
					for (let i = 0; i < dataArray.length; i++) {
						sum += dataArray[i];
					}
					const average = (sum / dataArray.length / 255) * _sensitivity;

					_historyRef.current.push(Math.min(1, Math.max(0.05, average)));

					if (_historyRef.current.length > _historySize) {
						_historyRef.current.shift();
					}
				}
			}

			const rect = canvas.getBoundingClientRect();
			ctx.clearRect(0, 0, rect.width, rect.height);

			const computedBarColor = getComputedBarColor(canvas, _barColor);

			const step = _barWidth + _barGap;
			const barCount = Math.floor(rect.width / step);
			const centerY = rect.height / 2;

			const dataToRender = _historyRef.current;

			if (dataToRender.length > 0) {
				const offsetInBars = Math.floor(_dragOffset / step);

				for (let i = 0; i < barCount; i++) {
					let dataIndex: number;

					if (_active) {
						dataIndex = dataToRender.length - 1 - i;
					} else {
						dataIndex = Math.max(
							0,
							Math.min(
								dataToRender.length - 1,
								dataToRender.length - 1 - i - Math.floor(offsetInBars)
							)
						);
					}

					if (dataIndex >= 0 && dataIndex < dataToRender.length) {
						const value = dataToRender[dataIndex];
						if (value !== undefined) {
							const x = rect.width - (i + 1) * step;
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

		if (_active || _historyRef.current.length > 0 || _playbackPosition !== null) {
			animationRef = requestAnimationFrame(animate);
		}

		return () => {
			if (animationRef !== null) {
				cancelAnimationFrame(animationRef);
				animationRef = null;
			}
		};
	});

	// --- Drag handling (replaces the isDragging useEffect) ---

	function handlePointerDown(event: PointerEvent) {
		if (active || historyRef.current.length === 0) return;

		event.preventDefault();
		dragStartXRef = event.clientX;
		dragStartOffsetRef = dragOffset;

		let lastScrubTime = 0;
		let lastMouseX = dragStartXRef;

		const handleMove = (moveEvent: PointerEvent) => {
			const deltaX = moveEvent.clientX - dragStartXRef;
			const newOffset = dragStartOffsetRef - deltaX * 0.5; // reduce sensitivity

			const step = barWidth + barGap;
			const maxBars = historyRef.current.length;
			const viewWidth = canvasEl?.getBoundingClientRect().width || 0;
			const viewBars = Math.floor(viewWidth / step);

			const maxOffset = Math.max(0, (maxBars - viewBars) * step);
			const minOffset = 0;
			const clampedOffset = Math.max(minOffset, Math.min(maxOffset, newOffset));

			setDragOffset(clampedOffset);

			const now = Date.now();
			if (enableAudioPlayback && audioBufferRef && now - lastScrubTime > 50) {
				lastScrubTime = now;
				const offsetBars = Math.floor(clampedOffset / step);
				const rightmostBarIndex = Math.max(0, Math.min(maxBars - 1, maxBars - 1 - offsetBars));
				const audioPosition = (rightmostBarIndex / maxBars) * audioBufferRef.duration;
				const direction = moveEvent.clientX - lastMouseX;
				lastMouseX = moveEvent.clientX;
				playScrubSound(
					Math.max(0, Math.min(audioBufferRef.duration - 0.1, audioPosition)),
					direction
				);
			}
		};

		const handleUp = () => {
			if (enableAudioPlayback && audioBufferRef) {
				const step = barWidth + barGap;
				const maxBars = historyRef.current.length;
				const offsetBars = Math.floor(dragOffset / step);
				const rightmostBarIndex = Math.max(0, Math.min(maxBars - 1, maxBars - 1 - offsetBars));
				const audioPosition = (rightmostBarIndex / maxBars) * audioBufferRef.duration;
				playFromPosition(Math.max(0, Math.min(audioBufferRef.duration - 0.1, audioPosition)));
			}

			if (scrubSourceRef) {
				try {
					scrubSourceRef.stop();
				} catch {
					// ignore
				}
			}

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
	data-slot="live-microphone-waveform"
	class={cn(
		"relative flex items-center",
		!active && historyRef.current.length > 0 && "cursor-pointer",
		className
	)}
	role={!active && historyRef.current.length > 0 ? "slider" : undefined}
	aria-label={!active && historyRef.current.length > 0
		? "Drag to scrub through recording"
		: undefined}
	aria-valuenow={!active && historyRef.current.length > 0 ? Math.abs(dragOffset) : undefined}
	aria-valuemin={!active && historyRef.current.length > 0 ? 0 : undefined}
	aria-valuemax={!active && historyRef.current.length > 0 ? historyRef.current.length : undefined}
	tabindex={!active && historyRef.current.length > 0 ? 0 : undefined}
	style:height={heightStyle}
	onpointerdown={handlePointerDown}
	{...restProps}
>
	<canvas bind:this={canvasEl} class="block h-full w-full"></canvas>
</div>
