<script lang="ts">
  import Card from '$lib/tailwind/logo-card/card.svelte';
  import Row from '$lib/tailwind/logo-card/row.svelte';
  import type { Stats } from './[dbId=id]/+page.js';
  import { toTitleCase } from '$lib/util.js';
  import { resolve } from '$app/paths';
  import prettyBytes from 'pretty-bytes';
  import { CircleStack } from 'svelte-heros-v2';

  interface Props {
    stats: Stats;
  }

  let { stats }: Props = $props();
</script>

<Card
  href={resolve('/__admin/dbs/[dbId=id]', { dbId: stats.name })}
  title={toTitleCase(stats.name)}
>
  {#snippet logo()}
    <CircleStack class="size-7 text-gray-900 dark:text-gray-100" />
  {/snippet}

  <Row title="File System Usage">
    {prettyBytes(stats.estimateLiveDataSize, { binary: true, maximumFractionDigits: 1 })}
  </Row>
  <Row title="Usable Space">
    {prettyBytes(stats.usableSpace, { binary: true, maximumFractionDigits: 1 })}
  </Row>
  {#if stats.blockCache}
    <Row title="Block Cache Usage">
      {(stats.blockCache.usage / stats.blockCache.capacity).toLocaleString(undefined, {
        style: 'percent',
        minimumFractionDigits: 0
      })}
      of
      {prettyBytes(stats.blockCache.capacity, {
        binary: true,
        maximumFractionDigits: 1
      })}
    </Row>
  {/if}
  <Row title="Compactions">
    <span title="pending">{stats.compactions.pending}</span>
    /
    <span title="running">{stats.compactions.running}</span>
  </Row>
</Card>
