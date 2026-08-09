<script lang="ts">
  import type { Snippet } from 'svelte';

  interface Props {
    title: string;
    children?: Snippet;
  }

  let { title, children }: Props = $props();
</script>

<div
  class="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-2 bg-white px-4 py-10 sm:px-6 xl:px-8 dark:bg-gray-800"
>
  <dt class="text-sm leading-6 font-medium text-gray-500 dark:text-gray-400">{title}</dt>
  <dd
    class="w-full flex-none text-3xl leading-10 font-medium tracking-tight text-gray-900 dark:text-gray-100"
  >
    {@render children?.()}
  </dd>
</div>
