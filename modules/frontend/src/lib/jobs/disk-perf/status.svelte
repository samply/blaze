<script lang="ts">
  import type { DiskPerfJob } from '$lib/jobs/disk-perf';
  import Badge from '$lib/tailwind/badge.svelte';

  function progress(job: DiskPerfJob) {
    return job.phaseProgress ?? 0;
  }

  interface Props {
    job: DiskPerfJob;
  }

  let { job }: Props = $props();
</script>

{#if job.status === 'completed'}
  <Badge color="green" value="completed" />
{:else if job.status === 'failed'}
  <Badge color="red" value="failed" />
{:else if job.status === 'in-progress'}
  <Badge
    value={job.currentPhase ?? 'in-progress'}
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
