// Constant-rate sampling keeps scroll speed and detail identical across songs of different lengths.
export const DEFAULT_BARS_PER_SECOND = 8;

const SAMPLE_STRIDE = 100;
const QUIET_TRACK_BOOST = 3;
// 2dp matches the ≤51px-at-2×-DPR bar width and cuts shipped JSON size ~3×.
const AMPLITUDE_DECIMALS = 2;

/**
 * Returns `bars` normalized amplitude values in `[0, 1]` for a mono Float32 PCM channel.
 * Shared by the browser fallback and the Vite plugin so shipped JSONs and the runtime agree.
 */
export function sampleWaveform(channelData: Float32Array, bars: number): number[] {
	const samplesPerBar = Math.floor(channelData.length / bars);
	const quantum = 10 ** AMPLITUDE_DECIMALS;
	const out: number[] = [];
	for (let i = 0; i < bars; i++) {
		const start = i * samplesPerBar;
		const end = start + samplesPerBar;
		let sum = 0;
		let count = 0;
		for (let j = start; j < end && j < channelData.length; j += SAMPLE_STRIDE) {
			sum += Math.abs(channelData[j]);
			count++;
		}
		const avg = count > 0 ? sum / count : 0;
		out.push(Math.round(Math.min(1, avg * QUIET_TRACK_BOOST) * quantum) / quantum);
	}
	return out;
}

// Lazy fallback for tracks without a precomputed `waveformUrl` JSON shipped by the Vite plugin.
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
	// Context dimensions are placeholders: decodeAudioData returns a buffer sized to the source.
	const placeholderChannels = 1;
	const placeholderLength = 1;
	const placeholderSampleRate = 44100;
	const offlineContext = new OfflineCtx(placeholderChannels, placeholderLength, placeholderSampleRate);
	const audioBuffer = await offlineContext.decodeAudioData(arrayBuffer.slice(0));
	const bars = Math.max(1, Math.round(audioBuffer.duration * barsPerSecond));
	return sampleWaveform(audioBuffer.getChannelData(0), bars);
}
