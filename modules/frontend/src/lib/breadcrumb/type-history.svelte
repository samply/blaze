<script lang="ts">
  import { resolve } from '$app/paths';

  import Entry from './entry.svelte';

  interface Props {
    type: string;
    last?: boolean;
  }

  let { type, last = false }: Props = $props();
</script>

<Entry>
  {#if last}
    <span class="ml-4 text-sm font-medium text-gray-500 dark:text-gray-400">History</span>
  {:else}
    <a
      href={resolve('/[type=type]/_history', { type: type })}
      class="ml-4 text-sm font-medium text-gray-500 dark:text-gray-400 hover:text-gray-700 hover:dark:text-gray-300"
      >History</a
    >
  {/if}
</Entry>
