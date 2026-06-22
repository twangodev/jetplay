<script lang="ts">
  import {
    Menu, Play, Bug, GitBranch, Search, Settings,
    Folder, FolderOpen, ChevronDown, ChevronRight,
    FileText, FileCode, X, PanelLeft, ListTree, Terminal,
  } from '@lucide/svelte'
  import type { Snippet } from 'svelte'
  import MediaMark from './MediaMark.svelte'

  type TreeRow = {
    depth: number
    label: string
    kind: 'folder' | 'media' | 'code' | 'doc'
    expanded?: boolean
    selected?: boolean
    bold?: boolean
  }
  type Tab = { label: string; kind: 'media' | 'code'; active?: boolean }

  let {
    projectName = 'jetplay',
    runConfig = 'sintel',
    branch = 'main',
    version = '0.5.1',
    statusText = '',
    tree = [],
    tabs = [],
    children,
  }: {
    projectName?: string
    runConfig?: string
    branch?: string
    version?: string
    statusText?: string
    tree?: TreeRow[]
    tabs?: Tab[]
    children?: Snippet
  } = $props()

  const iconBtn = 'flex items-center justify-center size-7 rounded text-muted hover:bg-elevated hover:text-primary'
</script>

<div class="flex h-full w-full flex-col bg-surface text-primary select-none">
  <!-- main toolbar -->
  <div class="flex h-10 shrink-0 items-center gap-2 border-b border-border px-3">
    <button class={iconBtn} aria-label="Main menu"><Menu class="size-4" /></button>
    <div class="flex items-center gap-2 rounded px-2 py-1 hover:bg-elevated">
      <MediaMark size={16} />
      <span class="text-sm font-medium text-primary">{projectName}</span>
      <ChevronDown class="size-3 text-muted" />
    </div>
    <div class="flex-1"></div>
    <div class="flex h-7 items-center gap-1 rounded-md border border-border bg-elevated px-2">
      <MediaMark size={14} />
      <span class="text-[13px] text-primary">{runConfig}</span>
      <ChevronDown class="ml-0.5 size-3 text-muted" />
      <button class="ml-1 flex size-6 items-center justify-center rounded hover:bg-surface" aria-label="Run">
        <Play class="size-4 text-green-500" fill="currentColor" />
      </button>
      <button class="flex size-6 items-center justify-center rounded text-muted hover:bg-surface" aria-label="Debug">
        <Bug class="size-4" />
      </button>
    </div>
    <button class={iconBtn} aria-label="Search"><Search class="size-4" /></button>
    <button class={iconBtn} aria-label="Settings"><Settings class="size-4" /></button>
  </div>

  <!-- body: tool-window stripe + project panel + editor -->
  <div class="flex min-h-0 flex-1">
    <div class="flex w-11 shrink-0 flex-col items-center gap-1 border-r border-border pt-2">
      <div class="relative flex size-8 items-center justify-center rounded bg-elevated text-primary">
        <span class="absolute -left-1.5 h-5 w-0.5 rounded bg-accent"></span>
        <PanelLeft class="size-4" />
      </div>
      <div class="flex size-8 items-center justify-center rounded text-muted"><GitBranch class="size-4" /></div>
      <div class="flex size-8 items-center justify-center rounded text-muted"><ListTree class="size-4" /></div>
      <div class="flex-1"></div>
      <div class="mb-2 flex size-8 items-center justify-center rounded text-muted"><Terminal class="size-4" /></div>
    </div>

    <div class="flex w-64 shrink-0 flex-col border-r border-border">
      <div class="flex h-8 items-center px-3 text-[11px] font-semibold tracking-wide text-muted uppercase">Project</div>
      <div class="flex-1 overflow-hidden py-1">
        {#each tree as r (r.label)}
          {@render treeRow(r)}
        {/each}
      </div>
    </div>

    <div class="flex min-w-0 flex-1 flex-col bg-black">
      <div class="flex h-9 shrink-0 border-b border-border bg-surface">
        {#each tabs as t (t.label)}
          <div class={`relative flex items-center gap-1.5 border-r border-border px-3 text-[13px] ${t.active ? 'bg-black text-primary' : 'text-muted'}`}>
            {#if t.kind === 'media'}<MediaMark size={14} />{:else}<FileCode class="size-3.5 text-muted" />{/if}
            <span>{t.label}</span>
            <X class="ml-1 size-3.5 text-muted" />
            {#if t.active}<span class="absolute inset-x-0 -bottom-px h-0.5 bg-accent"></span>{/if}
          </div>
        {/each}
      </div>
      <div class="flex min-h-0 flex-1">
        {@render children?.()}
      </div>
    </div>
  </div>

  <!-- status bar -->
  <div class="flex h-6 shrink-0 items-center gap-4 border-t border-border px-3 text-[11px] text-muted">
    <div class="flex items-center gap-1"><GitBranch class="size-3" /><span>{branch}</span></div>
    <div class="flex-1"></div>
    {#if statusText}<span>{statusText}</span>{/if}
    <span>UTF-8</span>
    <span>{projectName}&nbsp;{version}</span>
  </div>
</div>

{#snippet treeRow(r: TreeRow)}
  <div
    class={`flex h-6 items-center gap-1 pr-2 text-[13px] ${r.selected ? 'bg-accent/30 text-white' : 'text-primary'}`}
    style={`padding-left:${8 + r.depth * 16}px`}
  >
    <span class="flex w-3.5 items-center justify-center text-muted">
      {#if r.kind === 'folder'}
        {#if r.expanded}<ChevronDown class="size-3.5" />{:else}<ChevronRight class="size-3.5" />{/if}
      {/if}
    </span>
    {#if r.kind === 'folder'}
      {#if r.expanded}<FolderOpen class="size-4 text-muted" />{:else}<Folder class="size-4 text-muted" />{/if}
    {:else if r.kind === 'media'}
      <MediaMark size={16} />
    {:else if r.kind === 'code'}
      <FileCode class="size-4 text-muted" />
    {:else}
      <FileText class="size-4 text-muted" />
    {/if}
    <span class={`truncate ${r.bold ? 'font-semibold' : ''}`}>{r.label}</span>
  </div>
{/snippet}
