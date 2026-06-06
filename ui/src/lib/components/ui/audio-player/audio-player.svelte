<script lang="ts" module>
	import type { Snippet } from "svelte";

	export type AudioPlayerProps = {
		/**
		 * Sub-components that read the shared player state via context
		 * (e.g. `<AudioPlayer.Button />`, `<AudioPlayer.Progress />`).
		 */
		children?: Snippet;
	};
</script>

<script lang="ts">
	import { setAudioPlayer } from "./context.svelte.js";

	let { children }: AudioPlayerProps = $props();

	const player = setAudioPlayer();

	let audioEl: HTMLAudioElement | null = $state(null);

	$effect(() => {
		player.audio = audioEl;
	});

	$effect(() => {
		let raf: number | null = null;
		const tick = () => {
			const el = player.audio;
			if (el) {
				player.time = el.currentTime;
				player.readyState = el.readyState;
				player.networkState = el.networkState;
				player.paused = el.paused;
				player.error = el.error;
				player.playbackRate = el.playbackRate;
				const d = el.duration;
				if (Number.isFinite(d) && player.duration !== d) {
					player.duration = d;
				}
			}
			raf = requestAnimationFrame(tick);
		};
		raf = requestAnimationFrame(tick);
		return () => {
			if (raf !== null) cancelAnimationFrame(raf);
		};
	});
</script>

<audio
	bind:this={audioEl}
	data-slot="audio-player"
	ondurationchange={(e) => {
		const d = e.currentTarget.duration;
		player.duration = Number.isFinite(d) ? d : undefined;
	}}
	onloadedmetadata={(e) => {
		const d = e.currentTarget.duration;
		player.duration = Number.isFinite(d) ? d : undefined;
	}}
	onplay={() => (player.paused = false)}
	onpause={() => (player.paused = true)}
	onerror={() => (player.error = audioEl?.error ?? null)}
	class="hidden"
	crossorigin="anonymous"
></audio>
{@render children?.()}
