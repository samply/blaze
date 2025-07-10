<script lang="ts">
  import type { PageProps } from './$types';

  import { base } from '$app/paths';
  import { invalidateAll } from '$app/navigation';
  import TaskRow from './task-row.svelte';

  let { data }: PageProps = $props();

  // reload page data every 10 seconds if at least one of the jobs is still in progress
  $effect(() => {
    const timeout = setTimeout(() => {
      invalidateAll();
    }, 10000);

    return () => {
      clearTimeout(timeout);
    };
  });
</script>

<svelte:head>
  <title>Jobs - Admin - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
  <div class="md:flex md:items-center md:justify-between">
    <h1 class="flex-1 text-base font-semibold leading-6 text-gray-900">All Jobs</h1>
    <div class="flex md:ml-4">
      <a
        class="rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white text-nowrap hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
        href="{base}/__admin/jobs/new"
      >
        New Job
      </a>
    </div>
  </div>

  <ul role="list" class="divide-y divide-gray-100 mt-4">
    {#each data.all as job (job.id)}
      <TaskRow {job} />
    {/each}
  </ul>
</main>
