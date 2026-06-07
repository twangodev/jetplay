/**
 * Lazy shared Web Audio graph for an audio element. Splits context creation
 * from analyser wiring so non-analyser callers (e.g. a scratch synth) can warm
 * the context from a user gesture without forcing a `createMediaElementSource`
 * call — only one is legal per element lifetime.
 */
export class AudioGraph {
	audioContext = $state<AudioContext | null>(null);
	analyser = $state<AnalyserNode | null>(null);
	#source: MediaElementAudioSourceNode | null = null;

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
		try {
			this.#source?.disconnect();
			this.analyser?.disconnect();
		} catch {
			// nodes may already be gone
		}
		void this.audioContext?.close().catch(() => {});
		this.audioContext = null;
		this.analyser = null;
		this.#source = null;
	}
}
