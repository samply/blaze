<script lang="ts">
  import type { Job } from '$lib/jobs';
  import Badge from '$lib/tailwind/badge.svelte';

  interface Props {
    job: Job;
  }

  let { job }: Props = $props();
</script>

{#if job.status === 'completed'}
  <Badge color="green" value="completed" />
{:else if job.status === 'failed'}
  <Badge color="red" value="failed" />
{:else if job.status === 'cancelled'}
  <Badge color="red" value="cancelled" />
{:else if job.status === 'in-progress'}
  <Badge color="blue" value="in-progress" />
{:else if job.status === 'on-hold'}
  {#if job.statusReason === 'paused'}
    <Badge color="blue" value="paused" />
  {:else if job.statusReason === 'orderly-shutdown'}
    <Badge color="yellow" value="orderly-shutdown" />
  {:else}
    <Badge color="yellow" value="on-hold" />
  {/if}
{:else}
  <Badge value={job.status} />
{/if}
