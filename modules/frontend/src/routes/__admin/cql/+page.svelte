<script lang="ts">
  import type { PageProps } from './$types';

  import Table from '$lib/tailwind/table/table.svelte';
  import BloomFilterRow from './bloom-filter-row.svelte';

  let { data }: PageProps = $props();
</script>

<svelte:head>
  <title>CQL - Admin - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
  <Table>
    {#snippet caption()}
      <div>
        <h1 class="text-base font-semibold leading-6 text-gray-900">Bloom filters</h1>
        <p class="mt-2 text-sm text-gray-700">
          Used to improve CQL performance. Only the newest 100 are shown.
        </p>
      </div>
    {/snippet}

    {#snippet head()}
      <tr>
        <th
          scope="col"
          class="py-3.5 pl-4 pr-3 text-right text-sm font-semibold text-gray-900 sm:pl-0">T</th
        >
        <th
          scope="col"
          class="py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 sm:pl-0">Hash</th
        >
        <th
          scope="col"
          class="whitespace-nowrap py-3.5 pl-4 pr-3 text-right text-sm font-semibold text-gray-900 sm:pl-0"
          ># Patients</th
        >
        <th
          scope="col"
          class="whitespace-nowrap py-3.5 pl-4 pr-3 text-right text-sm font-semibold text-gray-900 sm:pl-0"
          >Mem Size</th
        >
      </tr>
    {/snippet}

    {#each data.bloomFilters as bloomFilter (bloomFilter.hash)}
      <BloomFilterRow {bloomFilter} />
    {/each}
  </Table>
</main>
