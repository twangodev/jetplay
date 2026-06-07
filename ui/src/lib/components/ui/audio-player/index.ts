import Root from "./audio-player.svelte";
import Button from "./audio-player-button.svelte";
import Progress from "./audio-player-progress.svelte";
import Time from "./audio-player-time.svelte";
import Duration from "./audio-player-duration.svelte";
import Speed from "./audio-player-speed.svelte";
import SpeedButtonGroup from "./audio-player-speed-button-group.svelte";

export {
	Root,
	Button,
	Progress,
	Time,
	Duration,
	Speed,
	SpeedButtonGroup,
	//
	Root as AudioPlayer,
	Button as AudioPlayerButton,
	Progress as AudioPlayerProgress,
	Time as AudioPlayerTime,
	Duration as AudioPlayerDuration,
	Speed as AudioPlayerSpeed,
	SpeedButtonGroup as AudioPlayerSpeedButtonGroup,
};

export { setAudioPlayer, useAudioPlayer, AudioPlayerState } from "./context.svelte.js";
export type { AudioPlayerItem } from "./context.svelte.js";
export { formatTime } from "./utils.js";
export { exampleTracks } from "./example-tracks.js";
export { precomputeWaveform, sampleWaveform } from "./waveform-sampler.js";
export { AudioGraph } from "./audio-graph.svelte.js";
