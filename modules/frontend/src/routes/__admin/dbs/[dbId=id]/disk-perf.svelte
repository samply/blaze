<script lang="ts">
  import { invalidateAll } from '$app/navigation';
  import { resolve } from '$app/paths';
  import type { DiskPerfJob } from '$lib/jobs/disk-perf';
  import Results from '$lib/jobs/disk-perf/results.svelte';
  import Status from '$lib/jobs/disk-perf/status.svelte';
  import TimeAgo from '$lib/time-ago.svelte';

  interface Props {
    /** the most recent job with measurement results, if any */
    results?: DiskPerfJob;
    /** the currently running job, if any */
    running?: DiskPerfJob;
    /** error message of a failed job start */
    error?: string;
  }

  let { results, running, error }: Props = $props();

  // reload page data every 5 seconds while a measurement job is running
  $effect(() => {
    if (running !== undefined) {
      const timeout = setTimeout(() => {
        invalidateAll();
      }, 5000);

      return () => {
        clearTimeout(timeout);
      };
    }
  });
</script>

<div class="mt-8 sm:flex sm:items-end">
  <div class="sm:flex-auto">
    <h4 class="text-base font-semibold leading-6 text-gray-900 dark:text-gray-100">
      Disk Performance
    </h4>
    <p class="mt-1 text-sm leading-6 text-gray-500 dark:text-gray-400">
      {#if results}
        Results of <a
          class="hover:underline"
          href={resolve('/__admin/jobs/disk-perf/[id=id]', { id: results.id })}
          >job #{results.number}</a
        >
        finished <TimeAgo value={results.lastUpdated} />.
      {:else}
        No measurement results available.
      {/if}
    </p>
    {#if error}
      <p class="mt-1 text-sm leading-6 text-red-600 dark:text-red-400">{error}</p>
    {/if}
  </div>
  <div class="mt-4 sm:ml-16 sm:mt-0 sm:flex-none">
    {#if running}
      <Status job={running} />
    {:else}
      <form method="POST" action="?/diskPerf">
        <button
          type="submit"
          class="rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 enabled:cursor-pointer"
          >{results ? 'Refresh' : 'Start Measurement'}</button
        >
      </form>
    {/if}
  </div>
</div>

{#if results}
  <Results job={results} />
{/if}
