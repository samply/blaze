<script lang="ts">
  import { ChevronDown } from 'svelte-heros-v2';
  import type { ActionData } from './$types';
  import SubmitButtons from './../submit-buttons.svelte';

  interface Props {
    form: ActionData;
  }

  let { form }: Props = $props();

  let selectedDatabase = $derived(form?.database ?? 'index');
</script>

<svelte:head>
  <title>Create New Compact a Database Column Family Job - Admin - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
  <form method="POST">
    <div class="mt-6 grid grid-cols-1 gap-x-6 gap-y-8 sm:grid-cols-6">
      <div class="sm:col-span-2">
        <label
          for="database"
          class="block text-sm font-medium leading-6 text-gray-900 dark:text-gray-100"
          >Database</label
        >
        <div class="mt-2 grid grid-cols-1">
          <select
            id="database"
            name="database"
            bind:value={selectedDatabase}
            class="col-start-1 row-start-1 w-full appearance-none rounded-md bg-white dark:bg-gray-800 py-1.5 pr-8 pl-3 text-base text-gray-900 dark:text-gray-100 outline-1 -outline-offset-1 outline-gray-300 dark:outline-gray-500 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-indigo-600 sm:text-sm/6"
          >
            <option value="index">Index</option>
            <option value="transaction">Transaction</option>
            <option value="resource">Resource</option>
          </select>
          <ChevronDown
            variation="mini"
            class="pointer-events-none col-start-1 row-start-1 mr-2 size-5 self-center justify-self-end text-gray-500 dark:text-gray-400 sm:size-4"
          />
        </div>
      </div>

      <div class="col-span-full">
        <label
          for="column-family"
          class="block text-sm font-medium leading-6 text-gray-900 dark:text-gray-100"
          >Column Family</label
        >
        <div class="mt-2 grid grid-cols-1">
          <select
            id="column-family"
            name="column-family"
            class="col-start-1 row-start-1 w-full appearance-none rounded-md bg-white dark:bg-gray-800 py-1.5 pr-8 pl-3 text-base text-gray-900 dark:text-gray-100 outline-1 -outline-offset-1 outline-gray-300 dark:outline-gray-500 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-indigo-600 sm:text-sm/6"
          >
            {#if selectedDatabase === 'index'}
              <option value="search-param-value-index">SearchParamValueIndex</option>
              <option value="resource-value-index">ResourceValueIndex</option>
              <option value="compartment-search-param-value-index"
                >CompartmentSearchParamValueIndex</option
              >
              <option value="compartment-resource-type-index">CompartmentResourceTypeIndex</option>
              <option value="active-search-params">ActiveSearchParams</option>
              <option value="tx-success-index">TxSuccessIndex</option>
              <option value="tx-error-index">TxErrorIndex</option>
              <option value="t-by-instant-index">TByInstantIndex</option>
              <option value="resource-as-of-index">ResourceAsOfIndex</option>
              <option value="type-as-of-index">TypeAsOfIndex</option>
              <option value="system-as-of-index">SystemAsOfIndex</option>
              <option value="patient-last-change-index">PatientLastChangeIndex</option>
              <option value="type-stats-index">TypeStatsIndex</option>
              <option value="system-stats-index">SystemStatsIndex</option>
              <option value="cql-bloom-filter">CqlBloomFilter</option>
              <option value="cql-bloom-filter-by-t">CqlBloomFilterByT</option>
            {:else}
              <option value="default">Default</option>
            {/if}
          </select>
          <ChevronDown
            variation="mini"
            class="pointer-events-none col-start-1 row-start-1 mr-2 size-5 self-center justify-self-end text-gray-500 dark:text-gray-400 sm:size-4"
          />
        </div>
      </div>

      {#if form?.incorrect}
        <div class="col-span-full">
          <p class="mt-2 text-sm text-red-600 dark:text-red-400">{form?.msg}</p>
        </div>
      {/if}
    </div>
    <SubmitButtons />
  </form>
</main>
