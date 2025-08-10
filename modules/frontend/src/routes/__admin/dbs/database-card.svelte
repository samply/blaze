<script lang="ts">
  import Card from '$lib/tailwind/logo-card/card.svelte';
  import Row from '$lib/tailwind/logo-card/row.svelte';
  import type { Stats } from './[dbId=id]/+page.js';
  import { toTitleCase } from '$lib/util.js';
  import { resolve } from '$app/paths';
  import prettyBytes from 'pretty-bytes';

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
    <svg
      xmlns="http://www.w3.org/2000/svg"
      fill="none"
      viewBox="0 0 24 24"
      stroke-width="1.5"
      stroke="currentColor"
    >
      <path
        stroke-linecap="round"
        stroke-linejoin="round"
        d="M20.25 6.375c0 2.278-3.694 4.125-8.25 4.125S3.75 8.653 3.75 6.375m16.5 0c0-2.278-3.694-4.125-8.25-4.125S3.75 4.097 3.75 6.375m16.5 0v11.25c0 2.278-3.694 4.125-8.25 4.125s-8.25-1.847-8.25-4.125V6.375m16.5 0v3.75m-16.5-3.75v3.75m16.5 0v3.75C20.25 16.153 16.556 18 12 18s-8.25-1.847-8.25-4.125v-3.75m16.5 0c0 2.278-3.694 4.125-8.25 4.125s-8.25-1.847-8.25-4.125"
      />
    </svg>
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
