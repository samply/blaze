<script lang="ts">
  import type { Snippet } from 'svelte';

  interface Props {
    /** Human-readable label (e.g., "Count") */
    label: string;
    /** Right-aligned value or parameter (e.g., "_summary=count") */
    value?: string;
    children?: Snippet;
  }

  let { label, value, children }: Props = $props();
</script>

<div class="py-2 first:pt-0 last:pb-0" role="listitem">
  <div class="flex items-baseline justify-between gap-x-3">
    <dt class="text-xs font-semibold text-gray-900 dark:text-gray-100">
      {label}
    </dt>
    {#if value}
      <dd class="shrink-0 font-mono text-[11px] font-medium text-gray-500 dark:text-gray-400">
        {value}
      </dd>
    {/if}
  </div>
  {#if children}
    <dd class="mt-0.5 text-xs leading-normal text-gray-600 dark:text-gray-300">
      {@render children()}
    </dd>
  {/if}
</div>
