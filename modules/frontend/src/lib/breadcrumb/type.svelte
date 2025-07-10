<script lang="ts">
  import { base } from '$app/paths';
  import { page } from '$app/state';
  import Entry from './entry.svelte';

  interface Props {
    type?: string;
    last?: boolean;
  }

  let { type = page.params.type, last = false }: Props = $props();
</script>

<Entry>
  {#if last}
    <span class="ml-4 text-sm font-medium text-gray-500">{type}</span>
  {:else}
    <a href="{base}/{type}" class="ml-4 text-sm font-medium text-gray-500 hover:text-gray-700"
      >{type}</a
    >
  {/if}
</Entry>
