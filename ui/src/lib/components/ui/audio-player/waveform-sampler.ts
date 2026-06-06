/**
 * Default rate used when callers don't pass one. Constant-rate sampling (as
 * opposed to a fixed total bar count) keeps scroll speed and detail identical
 * across songs of different lengths.
 */
export const DEFAULT_BARS_PER_SECOND = 8;

/**
 * Pure sampler: given a mono Float32 PCM channel, returns `bars` normalized
 * amplitude values in `[0, 1]`. The algorithm walks every 100th sample in each
 * bucket to stay cheap and multiplies by 3 so quiet tracks still read visually.
 *
 * Values are quantized to 2 decimal places — bars render at ≤51 physical px at
 * 2× DPR, so finer precision isn't visible and 2dp cuts shipped JSON size ~3×.
 *
 * The same function runs in both the browser (inside `precomputeWaveform`
 * below) and the Vite plugin (`vite/waveforms-plugin.ts`) so shipped JSONs
 * and the runtime fallback agree by construction.
 */
export function sampleWaveform(channelData: Float32Array, bars: number): number[] {
	const samplesPerBar = Math.floor(channelData.length / bars);
	const out: number[] = [];
	for (let i = 0; i < bars; i++) {
		const start = i * samplesPerBar;
		const end = start + samplesPerBar;
		let sum = 0;
		let count = 0;
		for (let j = start; j < end && j < channelData.length; j += 100) {
			sum += Math.abs(channelData[j]);
			count++;
		}
		const avg = count > 0 ? sum / count : 0;
		out.push(Math.round(Math.min(1, avg * 3) * 100) / 100);
	}
	return out;
}

/**
 * Browser fallback: fetch an audio URL, decode it via `OfflineAudioContext`,
 * and sample the first channel at `barsPerSecond` amplitude values per second
 * of audio.
 *
 * Prefer shipping precomputed waveform JSONs (via the Vite plugin) and
 * pointing tracks at them with `waveformUrl`. This function is the lazy
 * fallback for tracks without one.
 */
export async function precomputeWaveform(
	url: string,
	barsPerSecond = DEFAULT_BARS_PER_SECOND
): Promise<number[]> {
	const response = await fetch(url);
	const arrayBuffer = await response.arrayBuffer();
	const OfflineCtx =
		window.OfflineAudioContext ||
		(window as unknown as { webkitOfflineAudioContext: typeof OfflineAudioContext })
			.webkitOfflineAudioContext;
	// Length is irrelevant — we only use the context to invoke decodeAudioData,
	// which returns an AudioBuffer sized to the source. Use the minimum valid
	// value to make that intent obvious.
	const offlineContext = new OfflineCtx(1, 1, 44100);
	const audioBuffer = await offlineContext.decodeAudioData(arrayBuffer.slice(0));
	const bars = Math.max(1, Math.round(audioBuffer.duration * barsPerSecond));
	return sampleWaveform(audioBuffer.getChannelData(0), bars);
}
