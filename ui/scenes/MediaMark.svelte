<script module lang="ts">
  // Per-instance id suffix so repeated marks don't collide on gradient ids.
  let counter = 0
</script>

<script lang="ts">
  let { size = 16 }: { size?: number } = $props()
  const id = `mm${counter++}`
</script>

<!-- jetplay's registered Media file-type mark (src/main/resources/META-INF/pluginIcon.svg). -->
<svg width={size} height={size} viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id={`base-${id}`} x1="0" y1="0" x2="0.35" y2="1">
      <stop offset="0%" stop-color="#34343A" />
      <stop offset="55%" stop-color="#1E1E22" />
      <stop offset="100%" stop-color="#131316" />
    </linearGradient>
    <radialGradient id={`glow-${id}`} cx="0.30" cy="0.10" r="0.95">
      <stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.16" />
      <stop offset="55%" stop-color="#FFFFFF" stop-opacity="0" />
    </radialGradient>
    <linearGradient id={`edge-${id}`} x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.85" />
      <stop offset="100%" stop-color="#FFFFFF" stop-opacity="0.22" />
    </linearGradient>
    <linearGradient id={`tri-${id}`} x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%" stop-color="#DCDCE0" />
      <stop offset="100%" stop-color="#B6B6BC" />
    </linearGradient>
    <clipPath id={`clip-${id}`}><rect x="3" y="9" width="34" height="22" rx="6.5" /></clipPath>
  </defs>
  <g clip-path={`url(#clip-${id})`}>
    <rect x="3" y="9" width="34" height="22" rx="6.5" fill={`url(#base-${id})`} />
    <rect x="3" y="9" width="34" height="22" rx="6.5" fill={`url(#glow-${id})`} />
  </g>
  <rect x="3" y="9" width="34" height="22" rx="6.5" fill="none" stroke={`url(#edge-${id})`} stroke-width="0.8" />
  <polygon points="17.8,16.3 17.8,23.7 24.4,20" fill={`url(#tri-${id})`} />
</svg>
