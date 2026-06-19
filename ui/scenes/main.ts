import { mount } from 'svelte'
import './theme.css'
import HeroScene from './HeroScene.svelte'
import WindowAudioScene from './WindowAudioScene.svelte'
import IdeVideoScene from './IdeVideoScene.svelte'

// Marketing scenes for the JetBrains Marketplace listing. Rendered headless by
// scenes/render.mjs; window.jetplay config is injected before this runs.
const scene = new URLSearchParams(location.search).get('scene')
const Component =
  scene === 'audio-window' ? WindowAudioScene : scene === 'ide-video' ? IdeVideoScene : HeroScene

mount(Component, { target: document.getElementById('app')! })
