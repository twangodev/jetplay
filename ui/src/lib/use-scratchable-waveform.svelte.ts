import type { AudioGraph, AudioPlayerState } from "$lib/components/ui/audio-player/index.js";

// Module-level cache, intentionally non-reactive: keyed by track URL so
// navigating away and back reuses the decode, and concurrent pointerdowns
// dedupe onto one in-flight promise.
// eslint-disable-next-line svelte/prefer-svelte-reactivity
const scratchBufferCache = new Map<string, Promise<AudioBuffer | null>>();

async function fetchScratchBuffer(url: string, ctx: AudioContext): Promise<AudioBuffer | null> {
	try {
		const response = await fetch(url);
		const arrayBuffer = await response.arrayBuffer();
		return await ctx.decodeAudioData(arrayBuffer);
	} catch (error) {
		console.warn("Scratch buffer decode failed:", error);
		return null;
	}
}

function getScratchBuffer(url: string, ctx: AudioContext): Promise<AudioBuffer | null> {
	let cached = scratchBufferCache.get(url);
	if (!cached) {
		cached = fetchScratchBuffer(url, ctx);
		scratchBufferCache.set(url, cached);
	}
	return cached;
}

export interface ScratchableWaveformOptions<T = unknown> {
	player: AudioPlayerState<T>;
	graph: AudioGraph;
	trackUrl: () => string | null;
	totalWidth: () => number;
	containerWidth: () => number;
}

export function useScratchableWaveform<T = unknown>(opts: ScratchableWaveformOptions<T>) {
	let isScrubbing = $state(false);
	let isMomentumActive = $state(false);
	let offset = $state(0);

	let scratchBuffer: AudioBuffer | null = null;
	let scratchSource: AudioBufferSourceNode | null = null;

	function warmScratchBuffer(): void {
		if (scratchBuffer) return;
		const url = opts.trackUrl();
		const ctx = opts.graph.ensureContext();
		if (!url || !ctx) return;
		void getScratchBuffer(url, ctx).then((buf) => {
			if (buf && opts.trackUrl() === url) scratchBuffer = buf;
		});
	}

	function setOffsetAndSeek(next: number): void {
		offset = next;
		const totalW = opts.totalWidth();
		if (totalW <= 0) return;
		// Playhead pinned at the RIGHT edge (sv11): offset containerWidth = start,
		// containerWidth - totalW = end. The waveform fills in from the right and
		// scrolls left as it plays.
		const position = Math.max(0, Math.min(1, (opts.containerWidth() - next) / totalW));
		const audio = opts.player.audio;
		if (audio && isFinite(audio.duration)) audio.currentTime = position * audio.duration;
	}

	function playScratch(position: number, speed: number): void {
		const ctx = opts.graph.ensureContext();
		if (!ctx || !scratchBuffer) return;
		if (ctx.state === "suspended") void ctx.resume().catch(() => {});
		stopScratch();
		try {
			const source = ctx.createBufferSource();
			source.buffer = scratchBuffer;
			const startTime = Math.max(
				0,
				Math.min(scratchBuffer.duration - 0.1, position * scratchBuffer.duration)
			);
			const filter = ctx.createBiquadFilter();
			filter.type = "lowpass";
			filter.frequency.value = Math.max(800, 2500 - speed * 1500);
			filter.Q.value = 3;
			source.playbackRate.value = Math.max(0.4, Math.min(2.5, 1 + speed * 0.5));
			source.connect(filter);
			filter.connect(ctx.destination);
			source.start(0, startTime, 0.06);
			scratchSource = source;
		} catch (error) {
			console.error("scratch playback failed:", error);
		}
	}

	function stopScratch(): void {
		if (!scratchSource) return;
		try {
			scratchSource.stop();
		} catch {
			// already stopped
		}
		scratchSource = null;
	}

	function handlePointerDown(e: PointerEvent): void {
		if (e.pointerType === "mouse" && e.button !== 0) return;
		e.preventDefault();
		warmScratchBuffer();

		isScrubbing = true;
		const wasPlaying = opts.player.isPlaying;
		if (wasPlaying) void opts.player.pause();

		const startX = e.clientX;
		const totalW = opts.totalWidth();
		const containerW = opts.containerWidth();
		const startOffset = offset;
		// Right-pinned playhead: offset ranges [containerW - totalW, containerW].
		const clamp = (v: number) => Math.max(containerW - totalW, Math.min(containerW, v));
		const posAt = (o: number) => Math.max(0, Math.min(1, (containerW - o) / totalW));

		let lastPointerX = startX;
		let lastScratchTime = 0;
		let velocity = 0;
		let lastTime = Date.now();
		let lastClientX = e.clientX;

		const onMove = (ev: PointerEvent) => {
			if (ev.pointerId !== e.pointerId) return;
			const clamped = clamp(startOffset + (ev.clientX - startX));
			setOffsetAndSeek(clamped);

			const now = Date.now();
			const pointerDelta = ev.clientX - lastPointerX;
			const timeDelta = now - lastTime;
			if (timeDelta > 0) {
				const instant = (ev.clientX - lastClientX) / timeDelta;
				velocity = velocity * 0.6 + instant * 0.4;
			}
			lastTime = now;
			lastClientX = ev.clientX;

			if (pointerDelta !== 0 && now - lastScratchTime >= 10) {
				playScratch(posAt(clamped), Math.min(3, Math.abs(pointerDelta) / 3));
				lastScratchTime = now;
			}
			lastPointerX = ev.clientX;
		};

		const onEnd = (ev: PointerEvent) => {
			if (ev.pointerId !== e.pointerId) return;
			document.removeEventListener("pointermove", onMove);
			document.removeEventListener("pointerup", onEnd);
			document.removeEventListener("pointercancel", onEnd);

			isScrubbing = false;
			stopScratch();

			if (Math.abs(velocity) <= 0.1) {
				if (wasPlaying) void opts.player.play();
				return;
			}

			isMomentumActive = true;
			let current = offset;
			let v = velocity * 15;
			let lastFrame = 0;

			const step = () => {
				if (Math.abs(v) <= 0.5) {
					stopScratch();
					isMomentumActive = false;
					// Delay the resume so the final scratch slice releases
					// before the media element starts — prevents a crackle.
					if (wasPlaying) setTimeout(() => void opts.player.play(), 10);
					return;
				}
				current += v;
				v *= 0.92;
				const clamped = clamp(current);
				if (clamped !== current) v = 0;
				current = clamped;
				setOffsetAndSeek(clamped);

				const now = Date.now();
				if (now - lastFrame >= 50) {
					const speed = Math.min(2.5, Math.abs(v) / 10);
					if (speed > 0.1) playScratch(posAt(clamped), speed);
					lastFrame = now;
				}
				requestAnimationFrame(step);
			};
			requestAnimationFrame(step);
		};

		document.addEventListener("pointermove", onMove);
		document.addEventListener("pointerup", onEnd);
		document.addEventListener("pointercancel", onEnd);
	}

	function handleKeyDown(e: KeyboardEvent): void {
		const audio = opts.player.audio;
		if (!audio || !isFinite(audio.duration) || audio.duration <= 0) return;
		const step = e.shiftKey ? 5 : 1;
		const currentPct = (audio.currentTime / audio.duration) * 100;
		let nextPct: number | null = null;
		if (e.key === "ArrowLeft") nextPct = Math.max(0, currentPct - step);
		else if (e.key === "ArrowRight") nextPct = Math.min(100, currentPct + step);
		else if (e.key === "Home") nextPct = 0;
		else if (e.key === "End") nextPct = 100;
		if (nextPct === null) return;
		e.preventDefault();
		audio.currentTime = (nextPct / 100) * audio.duration;
	}

	function reset(): void {
		scratchBuffer = null;
		stopScratch();
	}

	return {
		get isScrubbing() {
			return isScrubbing;
		},
		get isMomentumActive() {
			return isMomentumActive;
		},
		get offset() {
			return offset;
		},
		set offset(value: number) {
			offset = value;
		},
		handlePointerDown,
		handleKeyDown,
		reset,
	};
}
