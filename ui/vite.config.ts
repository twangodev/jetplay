import { defineConfig } from 'vite'
import { fileURLToPath } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'
import { viteSingleFile } from 'vite-plugin-singlefile'

export default defineConfig({
  plugins: [tailwindcss(), svelte(), viteSingleFile()],
  resolve: {
    alias: {
      $lib: fileURLToPath(new URL('./src/lib', import.meta.url)),
    },
  },
  build: {
    outDir: '../frontend/src/main/resources/player',
    emptyOutDir: true,
  },
})