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
        <h1 class="text-base leading-6 font-semibold text-gray-900 dark:text-gray-100">
          Bloom filters
        </h1>
        <p class="mt-2 text-sm text-gray-700 dark:text-gray-300">
          Used to improve CQL performance. Only the newest 100 are shown.
        </p>
      </div>
    {/snippet}

    {#snippet head()}
      <tr>
        <th
          scope="col"
          class="py-3.5 pr-3 pl-4 text-right text-sm font-semibold text-gray-900 sm:pl-0 dark:text-gray-100"
          >T</th
        >
        <th
          scope="col"
          class="py-3.5 pr-3 pl-4 text-left text-sm font-semibold text-gray-900 sm:pl-0 dark:text-gray-100"
          >Hash</th
        >
        <th
          scope="col"
          class="py-3.5 pr-3 pl-4 text-right text-sm font-semibold whitespace-nowrap text-gray-900 sm:pl-0 dark:text-gray-100"
          ># Patients</th
        >
        <th
          scope="col"
          class="py-3.5 pr-3 pl-4 text-right text-sm font-semibold whitespace-nowrap text-gray-900 sm:pl-0 dark:text-gray-100"
          >Mem Size</th
        >
      </tr>
    {/snippet}

    {#each data.bloomFilters as bloomFilter (bloomFilter.hash)}
      <BloomFilterRow {bloomFilter} />
    {/each}
  </Table>
</main>
