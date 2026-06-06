import Root from "./waveform.svelte";
import Scrolling from "./waveform-scrolling.svelte";
import Scrubber from "./waveform-scrubber.svelte";
import Microphone from "./waveform-microphone.svelte";
import Static from "./waveform-static.svelte";
import LiveMicrophone from "./waveform-live-microphone.svelte";
import Recording from "./waveform-recording.svelte";

export {
	Root,
	Scrolling,
	Scrubber,
	Microphone,
	Static,
	LiveMicrophone,
	Recording,
	//
	Root as Waveform,
	Scrolling as ScrollingWaveform,
	Scrubber as AudioScrubber,
	Microphone as MicrophoneWaveform,
	Static as StaticWaveform,
	LiveMicrophone as LiveMicrophoneWaveform,
	Recording as RecordingWaveform,
};
export type { WaveformProps } from "./waveform.svelte";
export type { ScrollingWaveformProps } from "./waveform-scrolling.svelte";
export type { AudioScrubberProps } from "./waveform-scrubber.svelte";
export type { MicrophoneWaveformProps } from "./waveform-microphone.svelte";
export type { StaticWaveformProps } from "./waveform-static.svelte";
export type { LiveMicrophoneWaveformProps } from "./waveform-live-microphone.svelte";
export type { RecordingWaveformProps } from "./waveform-recording.svelte";
export { seededRandom, heightToCssSize, getComputedBarColor } from "./utils.js";
