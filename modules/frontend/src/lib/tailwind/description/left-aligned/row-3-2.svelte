<script lang="ts">
  import type { Snippet } from 'svelte';

  interface Props {
    title: string;
    children?: Snippet;
  }

  let { title, children }: Props = $props();
</script>

<div class="px-4 py-6 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-0" role="listitem">
  <dt class="text-sm font-medium leading-6 text-gray-900 dark:text-gray-100">{title}</dt>
  <dd class="mt-1 text-sm leading-6 text-gray-700 dark:text-gray-300 sm:col-span-2 sm:mt-0">
    {@render children?.()}
  </dd>
</div>
