import { defineConfig } from 'vite'
import tailwindcss from '@tailwindcss/vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'
import { viteSingleFile } from 'vite-plugin-singlefile'

export default defineConfig({
  plugins: [tailwindcss(), svelte(), viteSingleFile()],
  build: {
    outDir: '../src/main/resources/player',
    emptyOutDir: true,
  },
})