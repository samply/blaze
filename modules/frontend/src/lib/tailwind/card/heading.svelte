<script lang="ts">
  import type { Snippet } from 'svelte';

  interface Props {
    title: string;
    subtitle?: string;
    children?: Snippet;
  }

  let { title, subtitle, children }: Props = $props();
</script>

<div class="group relative flex items-start space-x-3">
  {#if children}
    <div class="shrink-0">
      {@render children?.()}
    </div>
  {/if}
  <div class="min-w-0 flex-1">
    <div class="text-sm font-medium text-gray-900">{title}</div>
    {#if subtitle}
      <p class="text-sm text-gray-500">{subtitle}</p>
    {/if}
  </div>
</div>
