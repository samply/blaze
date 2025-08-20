<script lang="ts">
  import type { Snippet } from 'svelte';

  interface Props {
    title: string;
    children?: Snippet;
  }

  let { title, children }: Props = $props();
</script>

<div
  class="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-2 bg-white dark:bg-gray-800 px-4 py-10 sm:px-6 xl:px-8"
>
  <dt class="text-sm font-medium leading-6 text-gray-500 dark:text-gray-400">{title}</dt>
  <dd
    class="w-full flex-none text-3xl font-medium leading-10 tracking-tight text-gray-900 dark:text-gray-100"
  >
    {@render children?.()}
  </dd>
</div>
