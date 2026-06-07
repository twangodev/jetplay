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

<!--
	jetplay: no `crossorigin` here. The element doesn't feed a Web-Audio analyser
	(jetplay has no orb visualizers), so the attribute would only add a needless
	CORS dependency on the media element. (Upstream sv11 sets it for its orbs.)
-->
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
></audio>
{@render children?.()}
