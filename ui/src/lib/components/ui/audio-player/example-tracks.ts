export type ExampleTrack = {
	id: string;
	name: string;
	url: string;
	/** Precomputed waveform bars inlined. Highest priority. */
	waveform?: number[];
	/** URL to a JSON file containing precomputed bars. Second priority. */
	waveformUrl?: string;
};

const TRACK_NAMES = [
	"alpha",
	"bravo",
	"charlie",
	"delta",
	"echo",
	"foxtrot",
	"golf",
	"hotel",
	"india",
	"juliett",
] as const;

export const exampleTracks: ExampleTrack[] = TRACK_NAMES.map((name, i) => ({
	id: String(i),
	name: name.charAt(0).toUpperCase() + name.slice(1),
	url: `https://sv11.ui.twango.dev/audio/${name}.mp3`,
	waveformUrl: `https://sv11.ui.twango.dev/audio/waveforms/${name}.json`,
}));
