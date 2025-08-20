<script lang="ts">
  import type { ReIndexJob } from '$lib/jobs/re-index';
  import Badge from '$lib/tailwind/badge.svelte';

  function progress(job: ReIndexJob) {
    const total = job.totalResources;
    const processed = job.resourcesProcessed;
    return total !== undefined && total > 0 && processed !== undefined
      ? Math.ceil((100 * processed) / total)
      : 0;
  }

  interface Props {
    job: ReIndexJob;
  }

  let { job }: Props = $props();
</script>

{#if job.status === 'completed'}
  <Badge color="green" value="completed" />
{:else if job.status === 'failed'}
  <Badge color="red" value="failed" />
{:else if job.status === 'in-progress'}
  <Badge
    value="in-progress"
    class="bg-linear-to-r from-blue-200 to-blue-50 dark:from-blue-800 dark:to-blue-600"
    style="--tw-gradient-from-position: {progress(job)}%;
           --tw-gradient-to-position: {progress(job) + 5}%"
  />
{:else if job.status === 'on-hold'}
  {#if job.statusReason === 'paused'}
    <Badge color="blue" value="paused" />
  {:else if job.statusReason === 'orderly-shutdown'}
    <Badge color="yellow" value="orderly-shutdown" />
  {:else}
    <Badge color="yellow" value="on-hold" />
  {/if}
{:else}
  <Badge color="gray" value={job.status} />
{/if}
