<script lang="ts">
  import { bestReadIops, type DiskPerfJob } from '$lib/jobs/disk-perf';
  import ReadIopsChart from '$lib/jobs/disk-perf/read-iops-chart.svelte';
  import ReadRunsTable from '$lib/jobs/disk-perf/read-runs-table.svelte';
  import SimpleStats from '$lib/tailwind/stats/simple.svelte';
  import prettyNum from '$lib/pretty-num';
  import prettyBytes from 'pretty-bytes';

  function throughput(bytesPerSecond: number): string {
    return prettyBytes(bytesPerSecond, { binary: true, maximumFractionDigits: 1 }) + '/s';
  }

  interface Props {
    job: DiskPerfJob;
  }

  let { job }: Props = $props();

  let readIops = $derived(bestReadIops(job));
</script>

<dl
  class="mx-auto mt-4 grid grid-cols-1 gap-px bg-gray-900/5 sm:grid-cols-2 lg:grid-cols-4 border-y border-gray-200 dark:border-gray-600"
>
  <SimpleStats title="Score">
    {job.score}
    {#if job.rating}
      <span class="text-base text-gray-500 dark:text-gray-400">{job.rating}</span>
    {/if}
  </SimpleStats>
  {#if readIops !== undefined}
    <SimpleStats title="Best Random Read IOPS">
      {prettyNum(readIops)}
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
  <ReadIopsChart runs={job.readRuns} />
  <ReadRunsTable runs={job.readRuns} />
{/if}
