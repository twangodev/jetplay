export function formatTime(seconds: number): string {
	const hrs = Math.floor(seconds / 3600);
	const mins = Math.floor((seconds % 3600) / 60);
	const secs = Math.floor(seconds % 60);

	const formattedMins = mins < 10 ? `0${mins}` : mins;
	const formattedSecs = secs < 10 ? `0${secs}` : secs;

	return hrs > 0 ? `${hrs}:${formattedMins}:${formattedSecs}` : `${mins}:${formattedSecs}`;
}
