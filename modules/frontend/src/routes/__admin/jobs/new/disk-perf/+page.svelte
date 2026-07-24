<script lang="ts">
  import { ChevronDown } from 'svelte-heros-v2';
  import type { ActionData } from './$types';
  import { defaultParameters } from '$lib/jobs/disk-perf';
  import SubmitButtons from './../submit-buttons.svelte';

  interface Props {
    form: ActionData;
  }

  let { form }: Props = $props();

  let selectedDatabase = $derived(form?.database ?? 'index');
</script>

<svelte:head>
  <title>Create New Measure Disk Performance Job - Admin - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
  <p class="mt-4 text-sm text-gray-500 dark:text-gray-400">
    Measures the disk performance of the volume of a database directory with an I/O profile similar
    to the one Blaze produces. The benchmark writes a test file into the database directory and
    competes with regular traffic for I/O, so it's best run on an otherwise idle server.
  </p>
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

      <div class="sm:col-span-2">
        <label
          for="file-size"
          class="block text-sm font-medium leading-6 text-gray-900 dark:text-gray-100"
          >File Size (GiB)</label
        >
        <div class="mt-2">
          <input
            type="number"
            id="file-size"
            name="file-size"
            value={form?.fileSize ?? defaultParameters.fileSize}
            min="0.125"
            max="64"
            step="any"
            class="block w-full rounded-md bg-white dark:bg-gray-800 px-3 py-1.5 text-base text-gray-900 dark:text-gray-100 outline-1 -outline-offset-1 outline-gray-300 dark:outline-gray-500 placeholder:text-gray-400 focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-600 focus:dark:outline-indigo-300 sm:text-sm/6"
          />
        </div>
      </div>

      <div class="sm:col-span-2">
        <label
          for="phase-duration"
          class="block text-sm font-medium leading-6 text-gray-900 dark:text-gray-100"
          >Phase Duration (seconds)</label
        >
        <div class="mt-2">
          <input
            type="number"
            id="phase-duration"
            name="phase-duration"
            value={form?.phaseDuration ?? defaultParameters.phaseDuration}
            min="1"
            max="300"
            step="any"
            class="block w-full rounded-md bg-white dark:bg-gray-800 px-3 py-1.5 text-base text-gray-900 dark:text-gray-100 outline-1 -outline-offset-1 outline-gray-300 dark:outline-gray-500 placeholder:text-gray-400 focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-600 focus:dark:outline-indigo-300 sm:text-sm/6"
          />
        </div>
      </div>

      <div class="sm:col-span-2">
        <label
          for="max-concurrency"
          class="block text-sm font-medium leading-6 text-gray-900 dark:text-gray-100"
          >Max Concurrency</label
        >
        <div class="mt-2">
          <input
            type="number"
            id="max-concurrency"
            name="max-concurrency"
            value={form?.maxConcurrency ?? defaultParameters.maxConcurrency}
            min="1"
            max="1024"
            step="1"
            class="block w-full rounded-md bg-white dark:bg-gray-800 px-3 py-1.5 text-base text-gray-900 dark:text-gray-100 outline-1 -outline-offset-1 outline-gray-300 dark:outline-gray-500 placeholder:text-gray-400 focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-600 focus:dark:outline-indigo-300 sm:text-sm/6"
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
