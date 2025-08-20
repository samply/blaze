<script lang="ts">
  import { page } from '$app/state';
  import { isTabActive, withTab } from '$lib/util.js';

  interface Props {
    name: string;
    label: string;
  }

  let { name, label }: Props = $props();
</script>

{#if isTabActive(page.url, name)}
  <a
    href={withTab(page.url, name)}
    class="border-indigo-500 text-indigo-600 dark:text-indigo-300 whitespace-nowrap border-b-2 py-4 px-1 text-sm font-medium"
    aria-current="page">{label}</a
  >
{:else}
  <a
    href={withTab(page.url, name)}
    class="border-transparent text-gray-500 dark:text-gray-400 hover:border-gray-300 hover:dark:border-gray-500 hover:text-gray-700 hover:dark:text-gray-300 whitespace-nowrap border-b-2 py-4 px-1 text-sm font-medium"
    >{label}</a
  >
{/if}
