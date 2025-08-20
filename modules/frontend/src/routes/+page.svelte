<script lang="ts">
  import type { PageProps } from './$types';

  import Table from '$lib/tailwind/table/table.svelte';
  import ResourceRow from '$lib/resource-row.svelte';

  let { data }: PageProps = $props();
</script>

<svelte:head>
  <title>Home - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
  <Table>
    {#snippet caption()}
      <div>
        <h1 class="text-base font-semibold leading-6 text-gray-900 dark:text-gray-100">
          Resource Types
        </h1>
        <p class="mt-2 text-sm text-gray-700 dark:text-gray-300">
          A list of all resource types available in this Blaze instance with their total count.
        </p>
      </div>
    {/snippet}

    {#snippet head()}
      <tr>
        <th
          scope="col"
          class="py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 dark:text-gray-100 sm:pl-0"
          >Name</th
        >
        <th
          scope="col"
          class="py-3.5 pl-4 pr-3 text-right text-sm font-semibold text-gray-900 dark:text-gray-100 sm:pl-0"
          >Total Count</th
        >
      </tr>
    {/snippet}

    {#each data.resourceTypeTotals as resourceTypeTotal (resourceTypeTotal.name)}
      <ResourceRow {...resourceTypeTotal} />
    {/each}
  </Table>
</main>
