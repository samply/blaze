<script lang="ts">
  import type { PageProps } from './$types';

  import SimpleStats from '#lib/tailwind/stats/simple.svelte';
  import LoadingIndicator from '#lib/loading-indicator.svelte';
  import ResourceTypeTable from '#lib/home/resource-type-table.svelte';
  import DemographicsCard from '#lib/home/demographics-card.svelte';
  import { grandTotal, patientTotal } from '#lib/home/resource-type-totals.js';

  let { data }: PageProps = $props();

  const numberFormat = new Intl.NumberFormat('en');
</script>

<svelte:head>
  <title>Home - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
  <dl
    class="grid grid-cols-1 gap-px border-y border-gray-200 bg-gray-900/5 sm:grid-cols-3 dark:border-gray-600"
  >
    <SimpleStats title="Resources">
      {numberFormat.format(grandTotal(data.resourceTypeTotals))}
    </SimpleStats>
    <SimpleStats title="Resource Types">
      {numberFormat.format(data.resourceTypeTotals.length)}
    </SimpleStats>
    <SimpleStats title="Patients">
      {numberFormat.format(patientTotal(data.resourceTypeTotals))}
    </SimpleStats>
  </dl>

  <div class="mt-8 grid grid-cols-1 gap-x-12 gap-y-10 px-4 sm:px-0 lg:grid-cols-2">
    <section class={data.streamed.demographics ? '' : 'lg:col-span-2'}>
      <h1 class="text-base leading-6 font-semibold text-gray-900 dark:text-gray-100">
        Resource Types
      </h1>
      <p class="mt-2 text-sm text-gray-700 dark:text-gray-300">
        All resource types available in this Blaze instance with their total count.
      </p>
      <div class="mt-4">
        <ResourceTypeTable totals={data.resourceTypeTotals} />
      </div>
    </section>

    {#if data.streamed.demographics}
      <section>
        <h2 class="text-base leading-6 font-semibold text-gray-900 dark:text-gray-100">
          Patients by Age and Gender
        </h2>
        <p class="mt-2 text-sm text-gray-700 dark:text-gray-300">
          The age pyramid of all patients, calculated live by the CQL engine of Blaze.
        </p>
        <div class="mt-6">
          {#await data.streamed.demographics}
            <LoadingIndicator start={data.streamed.start} />
          {:then demographics}
            <DemographicsCard {demographics} />
          {:catch error}
            <p class="text-sm text-gray-700 dark:text-gray-300">
              The patient demographics could not be calculated: {error.message}
            </p>
          {/await}
        </div>
      </section>
    {/if}
  </div>
</main>
