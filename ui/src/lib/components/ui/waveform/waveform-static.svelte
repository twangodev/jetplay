<script lang="ts">
	import Waveform, { type WaveformProps } from "./waveform.svelte";
	import { seededRandom } from "./utils.js";

	export type StaticWaveformProps = WaveformProps & {
		bars?: number;
		seed?: number;
	};

	let { bars = 40, seed = 42, ...restProps }: StaticWaveformProps = $props();

	const data = $derived(Array.from({ length: bars }, (_, i) => 0.2 + seededRandom(seed + i) * 0.6));
</script>

<Waveform {data} {...restProps} />
