/** Context creation is split from analyser wiring so callers can warm the context without the one-per-element-lifetime `createMediaElementSource` call. */
export class AudioGraph {
	audioContext = $state<AudioContext | null>(null);
	analyser = $state<AnalyserNode | null>(null);
	#source: MediaElementAudioSourceNode | null = null;
	#scratchOutput: GainNode | null = null;
	#scratchMedia: HTMLMediaElement | null = null;
	#syncScratchVolume: (() => void) | null = null;

	ensureContext(): AudioContext | null {
		if (this.audioContext) return this.audioContext;
		try {
			const AC =
				window.AudioContext ||
				(window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
			this.audioContext = new AC();
		} catch (err) {
			console.error("AudioContext creation failed", err);
			return null;
		}
		return this.audioContext;
	}

	ensureScratchOutput(media: HTMLMediaElement): GainNode | null {
		const ctx = this.ensureContext();
		if (!ctx) return null;
		if (!this.#scratchOutput) {
			this.#scratchOutput = ctx.createGain();
			this.#scratchOutput.connect(ctx.destination);
		}
		if (this.#scratchMedia !== media) {
			if (this.#syncScratchVolume) this.#scratchMedia?.removeEventListener("volumechange", this.#syncScratchVolume);
			this.#scratchMedia = media;
			this.#syncScratchVolume = () => {
				this.#scratchOutput?.gain.setValueAtTime(media.muted ? 0 : media.volume, ctx.currentTime);
			};
			media.addEventListener("volumechange", this.#syncScratchVolume);
		}
		this.#syncScratchVolume?.();
		return this.#scratchOutput;
	}

	ensureAnalyser(audioEl: HTMLAudioElement): AnalyserNode | null {
		if (this.analyser) return this.analyser;
		const ctx = this.ensureContext();
		if (!ctx) return null;
		if (ctx.state === "suspended") void ctx.resume().catch(() => {});
		try {
			this.#source = ctx.createMediaElementSource(audioEl);
			const a = ctx.createAnalyser();
			a.fftSize = 512;
			a.smoothingTimeConstant = 0.7;
			this.#source.connect(a);
			a.connect(ctx.destination);
			this.analyser = a;
			return a;
		} catch (err) {
			console.error("analyser wire failed", err);
			return null;
		}
	}

	destroy(): void {
		if (this.#syncScratchVolume) this.#scratchMedia?.removeEventListener("volumechange", this.#syncScratchVolume);
		this.#scratchMedia = null;
		this.#syncScratchVolume = null;
		try {
			this.#source?.disconnect();
			this.analyser?.disconnect();
			this.#scratchOutput?.disconnect();
		} catch {
			// nodes may already be gone
		}
		void this.audioContext?.close().catch(() => {});
		this.audioContext = null;
		this.analyser = null;
		this.#source = null;
		this.#scratchOutput = null;
	}
}
