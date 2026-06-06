<script lang="ts">
	import { untrack } from "svelte";
	import Waveform, { type WaveformProps } from "./waveform.svelte";

	export type MicrophoneWaveformProps = WaveformProps & {
		active?: boolean;
		processing?: boolean;
		fftSize?: number;
		smoothingTimeConstant?: number;
		sensitivity?: number;
		onError?: (error: Error) => void;
	};

	let {
		active = false,
		processing = false,
		fftSize = 256,
		smoothingTimeConstant = 0.8,
		sensitivity = 1,
		onError,
		...restProps
	}: MicrophoneWaveformProps = $props();

	let data: number[] = $state([]);

	// Non-reactive refs (match React `useRef` usage).
	let analyserRef: AnalyserNode | null = null;
	let audioContextRef: AudioContext | null = null;
	let streamRef: MediaStream | null = null;
	let animationIdRef: number | null = null;
	let processingAnimationRef: number | null = null;
	let lastActiveDataRef: number[] = [];
	let transitionProgressRef = 0;

	// Processing / idle fade animation — keyed on [processing, active] in React.
	$effect(() => {
		const _processing = processing;
		const _active = active;

		if (_processing && !_active) {
			let time = 0;
			transitionProgressRef = 0;

			const animateProcessing = () => {
				time += 0.03;
				transitionProgressRef = Math.min(1, transitionProgressRef + 0.02);

				const processingData: number[] = [];
				const barCount = 45;

				for (let i = 0; i < barCount; i++) {
					const normalizedPosition = (i - barCount / 2) / (barCount / 2);
					const centerWeight = 1 - Math.abs(normalizedPosition) * 0.4;

					const wave1 = Math.sin(time * 1.5 + i * 0.15) * 0.25;
					const wave2 = Math.sin(time * 0.8 - i * 0.1) * 0.2;
					const wave3 = Math.cos(time * 2 + i * 0.05) * 0.15;
					const combinedWave = wave1 + wave2 + wave3;
					const processingValue = (0.2 + combinedWave) * centerWeight;

					let finalValue = processingValue;
					if (lastActiveDataRef.length > 0 && transitionProgressRef < 1) {
						const lastDataIndex = Math.floor((i / barCount) * lastActiveDataRef.length);
						const lastValue = lastActiveDataRef[lastDataIndex] || 0;
						finalValue =
							lastValue * (1 - transitionProgressRef) + processingValue * transitionProgressRef;
					}

					processingData.push(Math.max(0.05, Math.min(1, finalValue)));
				}

				data = processingData;
				processingAnimationRef = requestAnimationFrame(animateProcessing);
			};

			animateProcessing();

			return () => {
				if (processingAnimationRef !== null) {
					cancelAnimationFrame(processingAnimationRef);
					processingAnimationRef = null;
				}
			};
		} else if (!_active && !_processing) {
			const startData = untrack(() => data);
			if (startData.length > 0) {
				let fadeProgress = 0;
				const fadeToIdle = () => {
					fadeProgress += 0.03;
					if (fadeProgress < 1) {
						data = startData.map((value) => value * (1 - fadeProgress));
						requestAnimationFrame(fadeToIdle);
					} else {
						data = [];
					}
				};
				fadeToIdle();
			}
			return;
		}
	});

	// Mic capture — keyed on [active, fftSize, smoothingTimeConstant, sensitivity, onError].
	$effect(() => {
		const _active = active;
		const _fftSize = fftSize;
		const _smoothingTimeConstant = smoothingTimeConstant;
		const _sensitivity = sensitivity;
		const _onError = onError;

		if (!_active) {
			if (streamRef) {
				streamRef.getTracks().forEach((track) => track.stop());
			}
			if (audioContextRef && audioContextRef.state !== "closed") {
				audioContextRef.close();
			}
			if (animationIdRef !== null) {
				cancelAnimationFrame(animationIdRef);
			}
			return;
		}

		let cancelled = false;

		const setupMicrophone = async () => {
			try {
				const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
				if (cancelled) {
					stream.getTracks().forEach((track) => track.stop());
					return;
				}
				streamRef = stream;

				const AudioContextCtor =
					window.AudioContext ||
					(window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
				const audioContext = new AudioContextCtor();
				const analyser = audioContext.createAnalyser();
				analyser.fftSize = _fftSize;
				analyser.smoothingTimeConstant = _smoothingTimeConstant;

				const source = audioContext.createMediaStreamSource(stream);
				source.connect(analyser);

				audioContextRef = audioContext;
				analyserRef = analyser;

				const dataArray = new Uint8Array(analyser.frequencyBinCount);

				const updateData = () => {
					if (!analyserRef || cancelled) return;

					analyserRef.getByteFrequencyData(dataArray);

					const startFreq = Math.floor(dataArray.length * 0.05);
					const endFreq = Math.floor(dataArray.length * 0.4);
					const relevantData = dataArray.slice(startFreq, endFreq);

					const halfLength = Math.floor(relevantData.length / 2);
					const normalizedData: number[] = [];

					for (let i = halfLength - 1; i >= 0; i--) {
						const value = Math.min(1, (relevantData[i] / 255) * _sensitivity);
						normalizedData.push(value);
					}

					for (let i = 0; i < halfLength; i++) {
						const value = Math.min(1, (relevantData[i] / 255) * _sensitivity);
						normalizedData.push(value);
					}

					data = normalizedData;
					lastActiveDataRef = normalizedData;

					animationIdRef = requestAnimationFrame(updateData);
				};

				updateData();
			} catch (error) {
				_onError?.(error as Error);
			}
		};

		setupMicrophone();

		return () => {
			cancelled = true;
			if (streamRef) {
				streamRef.getTracks().forEach((track) => track.stop());
				streamRef = null;
			}
			if (audioContextRef && audioContextRef.state !== "closed") {
				audioContextRef.close();
				audioContextRef = null;
			}
			if (animationIdRef !== null) {
				cancelAnimationFrame(animationIdRef);
				animationIdRef = null;
			}
		};
	});
</script>

<Waveform {data} {...restProps} />
