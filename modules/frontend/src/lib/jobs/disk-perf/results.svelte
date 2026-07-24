<script lang="ts">
  import { bestReadRun, type DiskPerfJob } from '$lib/jobs/disk-perf';
  import ReadIopsChart from '$lib/jobs/disk-perf/read-iops-chart.svelte';
  import ReadLatencyChart from '$lib/jobs/disk-perf/read-latency-chart.svelte';
  import ReadRunsTable from '$lib/jobs/disk-perf/read-runs-table.svelte';
  import ScoreGauge from '$lib/jobs/disk-perf/score-gauge.svelte';
  import SimpleStats from '$lib/tailwind/stats/simple.svelte';
  import prettyNum from '$lib/pretty-num';
  import prettyBytes from 'pretty-bytes';
  import { ExclamationTriangle } from 'svelte-heros-v2';

  function throughput(bytesPerSecond: number): string {
    return prettyBytes(bytesPerSecond, { binary: true, maximumFractionDigits: 1 }) + '/s';
  }

  interface Props {
    job: DiskPerfJob;
  }

  let { job }: Props = $props();

  let bestRead = $derived(bestReadRun(job));
</script>

<dl
  class="mx-auto mt-4 grid grid-cols-1 gap-px bg-gray-900/5 sm:grid-cols-2 lg:grid-cols-4 border-y border-gray-200 dark:border-gray-600"
>
  {#if job.score !== undefined}
    <ScoreGauge score={job.score} rating={job.rating} />
  {/if}
  {#if bestRead?.iops !== undefined}
    <SimpleStats title="Best Random Read IOPS">
      {prettyNum(bestRead.iops)}
      <span class="text-base font-normal text-gray-500 dark:text-gray-400"
        >@ c{bestRead.concurrency}</span
      >
    </SimpleStats>
  {/if}
  {#if job.seqWriteThroughput !== undefined}
    <SimpleStats title="Sequential Write Throughput">
      {throughput(job.seqWriteThroughput)}
    </SimpleStats>
  {/if}
  {#if job.fsyncRate !== undefined}
    <SimpleStats title="Fsyncs per Second">
      {prettyNum(job.fsyncRate)}
    </SimpleStats>
  {/if}
</dl>

{#if job.readRuns}
  <h2 class="mt-8 text-base font-semibold leading-6 text-gray-900 dark:text-gray-100">
    Random Reads
  </h2>
  <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
    Random reads of 16 KiB blocks, sweeping the reader concurrency in powers of two.
  </p>

  {#if job.directIo === false}
    <div
      class="mt-4 flex gap-x-3 rounded-md border border-amber-300 bg-amber-50 p-4 text-sm text-amber-800 dark:border-amber-500/40 dark:bg-amber-500/10 dark:text-amber-200"
    >
      <ExclamationTriangle variation="mini" class="h-5 w-5 flex-none text-amber-500" />
      <p>
        Direct I/O was not available, so the random reads could not bypass the page cache. The
        results may reflect memory instead of the disk and can be too optimistic.
      </p>
    </div>
  {/if}

  <div class="mt-4 grid grid-cols-1 gap-x-8 xl:grid-cols-2">
    <ReadIopsChart runs={job.readRuns} />
    <ReadLatencyChart runs={job.readRuns} />
  </div>
  <ReadRunsTable runs={job.readRuns} />
{/if}
