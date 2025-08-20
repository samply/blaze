<script lang="ts">
  import type { Snippet } from 'svelte';

  interface Props {
    name: string;
    description?: string;
    children?: Snippet;
  }

  let { name, description, children }: Props = $props();
</script>

<div>
  <h2 class="text-base/7 font-semibold text-gray-900 dark:text-gray-100">{name}</h2>
  {#if description}
    <p class="mt-1 max-w-2xl text-sm/6 text-gray-600">{description}</p>
  {/if}

  <div
    class="mt-10 space-y-8 border-b border-gray-900/10 pb-12 sm:space-y-0 sm:divide-y sm:divide-gray-900/10 sm:border-t sm:pb-0"
  >
    {@render children?.()}
  </div>
</div>
