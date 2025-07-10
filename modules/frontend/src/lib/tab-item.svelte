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
    class="border-indigo-500 text-indigo-600 whitespace-nowrap border-b-2 py-4 px-1 text-sm font-medium"
    aria-current="page">{label}</a
  >
{:else}
  <a
    href={withTab(page.url, name)}
    class="border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 whitespace-nowrap border-b-2 py-4 px-1 text-sm font-medium"
    >{label}</a
  >
{/if}
