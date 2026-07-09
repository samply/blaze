<script lang="ts">
  import type { Snippet } from 'svelte';

  interface Props {
    children?: Snippet;
  }

  let { children }: Props = $props();
</script>

<dl class="divide-y divide-gray-100 dark:divide-gray-700" role="list">
  {@render children?.()}
</dl>
