<script lang="ts">
  import type { Snippet } from 'svelte';

  interface Props {
    children?: Snippet;
  }

  let { children }: Props = $props();
</script>

<nav class="flex pl-8 py-4 border-b border-gray-200 dark:border-gray-600" aria-label="Breadcrumb">
  <ol class="flex items-center py-0.5 space-x-4">
    {@render children?.()}
  </ol>
</nav>
