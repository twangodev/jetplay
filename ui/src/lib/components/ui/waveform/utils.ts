export function seededRandom(seed: number): number {
	const x = Math.sin(seed) * 10000;
	return x - Math.floor(x);
}

export function heightToCssSize(height: string | number): string {
	return typeof height === "number" ? `${height}px` : height;
}

export function getComputedBarColor(
	canvas: HTMLCanvasElement,
	override: string | undefined
): string {
	return override || getComputedStyle(canvas).getPropertyValue("--foreground") || "#000";
}
