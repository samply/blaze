<script lang="ts">
  import { resolve } from '$app/paths';

  import Entry from './entry.svelte';

  interface Props {
    type: string;
    id: string;
    last?: boolean;
  }

  let { type, id, last = false }: Props = $props();
</script>

<Entry>
  {#if last}
    <span class="ml-4 text-sm font-medium text-gray-500 dark:text-gray-400">History</span>
  {:else}
    <a
      href={resolve('/[type=type]/[id=id]/_history', { type: type, id: id })}
      class="ml-4 text-sm font-medium text-gray-500 dark:text-gray-400 hover:text-gray-700 hover:dark:text-gray-300"
      >History</a
    >
  {/if}
</Entry>
