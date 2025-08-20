<script lang="ts">
  import SimpleStats from '$lib/tailwind/stats/simple.svelte';
  import type { Stats } from './+page.js';
  import prettyBytes from 'pretty-bytes';

  interface Props {
    stats: Stats;
  }

  let { stats }: Props = $props();
</script>

<dl
  class="mx-auto grid grid-cols-1 gap-px bg-gray-900/5 sm:grid-cols-2 lg:grid-cols-4 border-y border-gray-200 dark:border-gray-600"
>
  <SimpleStats title="File System Usage">
    {prettyBytes(stats.estimateLiveDataSize, { binary: true, maximumFractionDigits: 1 })}
  </SimpleStats>
  <SimpleStats title="Usable Space">
    {prettyBytes(stats.usableSpace, { binary: true, maximumFractionDigits: 1 })}
  </SimpleStats>
  <SimpleStats title="Block Cache Usage">
    {#if stats.blockCache}
      {(stats.blockCache.usage / stats.blockCache.capacity).toLocaleString(undefined, {
        style: 'percent',
        minimumFractionDigits: 0
      })}
      of
      {prettyBytes(stats.blockCache.capacity, {
        binary: true,
        maximumFractionDigits: 1
      })}
    {:else}
      unused
    {/if}
  </SimpleStats>
  <SimpleStats title="Compactions">
    <span title="pending">{stats.compactions.pending}</span>
    /
    <span title="running">{stats.compactions.running}</span>
  </SimpleStats>
</dl>
