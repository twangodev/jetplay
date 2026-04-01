<script lang="ts">
  import { TriangleAlert } from '@lucide/svelte'

  let { message }: { message: string } = $props()
</script>

<div class="error">
  <div class="icon">
    <TriangleAlert size={32} strokeWidth={1.5} />
  </div>
  <div class="title">Unable to play this file</div>
  <div class="detail">{message}</div>
</div>

<style>
  .error {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 10px;
  }

  .icon {
    color: var(--error);
    opacity: 0.8;
    margin-bottom: 4px;
  }

  .title {
    font-size: 14px;
    font-weight: 500;
    color: var(--error);
  }

  .detail {
    font-size: 12px;
    color: var(--text-muted);
    max-width: 400px;
    text-align: center;
    word-break: break-word;
    line-height: 1.5;
  }
</style>