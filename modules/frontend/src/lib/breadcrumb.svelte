<script lang="ts">
  import type { Snippet } from 'svelte';

  interface Props {
    children?: Snippet;
  }

  let { children }: Props = $props();
</script>

<nav class="flex border-b border-gray-200 py-4 pl-8 dark:border-gray-600" aria-label="Breadcrumb">
  <ol class="flex items-center space-x-4 py-0.5">
    {@render children?.()}
  </ol>
</nav>
