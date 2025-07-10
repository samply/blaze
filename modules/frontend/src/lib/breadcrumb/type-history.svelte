<script lang="ts">
  import { base } from '$app/paths';
  import { page } from '$app/state';
  import Entry from './entry.svelte';

  interface Props {
    last?: boolean;
  }

  let { last = false }: Props = $props();
</script>

<Entry>
  {#if last}
    <span class="ml-4 text-sm font-medium text-gray-500">History</span>
  {:else}
    <a
      href="{base}/{page.params.type}/_history"
      class="ml-4 text-sm font-medium text-gray-500 hover:text-gray-700">History</a
    >
  {/if}
</Entry>
