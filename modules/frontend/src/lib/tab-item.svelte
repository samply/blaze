<script lang="ts">
  import { page } from '$app/state';
  import { isTabActive, withTab } from '$lib/util.js';

  interface Props {
    name: string;
    label: string;
  }

  let { name, label }: Props = $props();
</script>

<!-- eslint-disable svelte/no-navigation-without-resolve -->

{#if isTabActive(page.url, name)}
  <a
    href={withTab(page.url, name)}
    class="border-b-2 border-indigo-500 px-1 py-4 text-sm font-medium whitespace-nowrap text-indigo-600 dark:text-indigo-300"
    aria-current="page">{label}</a
  >
{:else}
  <a
    href={withTab(page.url, name)}
    class="border-b-2 border-transparent px-1 py-4 text-sm font-medium whitespace-nowrap text-gray-500 hover:border-gray-300 hover:text-gray-700 dark:text-gray-400 hover:dark:border-gray-500 hover:dark:text-gray-300"
    >{label}</a
  >
{/if}
