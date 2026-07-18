<script lang="ts">
  import type { PageProps } from './$types';

  import { invalidateAll } from '$app/navigation';
  import { pascalCase } from 'change-case';
  import DescriptionList from '$lib/tailwind/description/left-aligned/list.svelte';
  import Row from '$lib/tailwind/description/left-aligned/row-3-2.svelte';
  import Results from '$lib/jobs/disk-perf/results.svelte';
  import Status from '$lib/jobs/disk-perf/status.svelte';
  import DateTime from '$lib/values/date-time.svelte';
  import prettyMicros from '$lib/pretty-micros';
  import prettyNum from '$lib/pretty-num';
  import humanizeDuration from 'humanize-duration';

  let { data }: PageProps = $props();

  // reload page data every 5 seconds if the job is still in progress
  $effect(() => {
    if (data.job.status === 'ready' || data.job.status === 'in-progress') {
      const timeout = setTimeout(() => {
        invalidateAll();
      }, 5000);

      return () => {
        clearTimeout(timeout);
      };
    }
  });
</script>

<svelte:head>
  <title>Job #{data.job.number} - Admin - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
  <DescriptionList>
    {#snippet title()}
      Job #{data.job.number}
    {/snippet}
    {#snippet description()}
      Last Updated
      <DateTime value={data.job.lastUpdated} />
    {/snippet}
    <Row title="Status">
      <Status job={data.job} />
    </Row>
    <Row title="Type">Measure Disk Performance</Row>
    <Row title="Created">
      <DateTime value={data.job.authoredOn} />
    </Row>
    <Row title="Database">
      {pascalCase(data.job.database)}
    </Row>
    {#if data.job.fileSize}
      <Row title="File Size">
        {prettyNum(data.job.fileSize)} GiB
      </Row>
    {/if}
    {#if data.job.phaseDuration}
      <Row title="Phase Duration">
        {humanizeDuration(data.job.phaseDuration * 1000, { round: true })}
      </Row>
    {/if}
    {#if data.job.maxConcurrency}
      <Row title="Max Concurrency">
        {data.job.maxConcurrency}
      </Row>
    {/if}
    {#if data.job.error}
      <Row title="Error">
        {data.job.error}
      </Row>
    {/if}
    {#if data.job.directIo === false}
      <Row title="Direct I/O">
        not supported on this volume — the read results include page cache effects
      </Row>
    {/if}
    {#if data.job.processingDuration}
      <Row title="Processing Duration">
        {humanizeDuration(data.job.processingDuration * 1000, { round: true })}
      </Row>
    {/if}
  </DescriptionList>

  {#if data.job.score !== undefined}
    <h2 class="mt-8 text-base font-semibold leading-6 text-gray-900 dark:text-gray-100">Results</h2>
    <Results job={data.job} />

    <DescriptionList>
      {#snippet title()}
        Fsync Latencies
      {/snippet}
      {#snippet description()}
        Latency percentiles of single write + fsync operations.
      {/snippet}
      {#if data.job.fsyncLatencyP50 !== undefined}
        <Row title="Fsync P50">
          {prettyMicros(data.job.fsyncLatencyP50)}
        </Row>
      {/if}
      {#if data.job.fsyncLatencyP95 !== undefined}
        <Row title="Fsync P95">
          {prettyMicros(data.job.fsyncLatencyP95)}
        </Row>
      {/if}
      {#if data.job.fsyncLatencyP99 !== undefined}
        <Row title="Fsync P99">
          {prettyMicros(data.job.fsyncLatencyP99)}
        </Row>
      {/if}
    </DescriptionList>
  {/if}
</main>
