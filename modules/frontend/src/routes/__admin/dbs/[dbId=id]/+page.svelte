<script lang="ts">
  import type { PageProps } from './$types';

  import ColumnFamilyTable from './column-family-table.svelte';
  import { toTitleCase } from '$lib/util.js';
  import { descriptions } from '../util.js';
  import Stats from './stats.svelte';

  let { data, params }: PageProps = $props();
</script>

<svelte:head>
  <title>{toTitleCase(params.dbId)} Database - Admin - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
  <div class="mt-4 pb-5">
    <h3 class="text-base font-semibold leading-7 text-gray-900 dark:text-gray-100">
      {toTitleCase(data.stats.name)}
    </h3>
    <p class="mt-1 max-w-4xl text-sm leading-6 text-gray-500 dark:text-gray-400">
      {descriptions[data.stats.name]}
    </p>
  </div>

  <Stats stats={data.stats} />
  <ColumnFamilyTable dbId={params.dbId} columnFamilies={data.columnFamilies} />
</main>
