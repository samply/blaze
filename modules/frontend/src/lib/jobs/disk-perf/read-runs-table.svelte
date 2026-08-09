<script lang="ts">
  import type { RandReadRun } from '$lib/jobs/disk-perf';
  import Table from '$lib/tailwind/table/table.svelte';
  import prettyMicros from '$lib/pretty-micros';
  import prettyNum from '$lib/pretty-num';
  import prettyBytes from 'pretty-bytes';

  interface Props {
    /** runs of the random read sweep, ordered by ascending concurrency */
    runs: RandReadRun[];
  }

  let { runs }: Props = $props();

  function num(value: number | undefined): string {
    return value === undefined ? '–' : prettyNum(value);
  }

  function throughput(value: number | undefined): string {
    return value === undefined
      ? '–'
      : prettyBytes(value, { binary: true, maximumFractionDigits: 1 }) + '/s';
  }

  function micros(value: number | undefined): string {
    return value === undefined ? '–' : prettyMicros(value);
  }
</script>

<Table clazz="mt-4">
  {#snippet head()}
    <tr>
      <th
        scope="col"
        class="py-3.5 pr-3 pl-4 text-left text-sm font-semibold text-gray-900 sm:pl-0 dark:text-gray-100"
        >Concurrency</th
      >
      <th
        scope="col"
        class="px-3 py-3.5 text-right text-sm font-semibold text-gray-900 dark:text-gray-100"
        >IOPS</th
      >
      <th
        scope="col"
        class="px-3 py-3.5 text-right text-sm font-semibold text-gray-900 dark:text-gray-100"
        >Throughput</th
      >
      <th
        scope="col"
        class="px-3 py-3.5 text-right text-sm font-semibold text-gray-900 dark:text-gray-100"
        >Latency P50</th
      >
      <th
        scope="col"
        class="px-3 py-3.5 text-right text-sm font-semibold text-gray-900 dark:text-gray-100"
        >P95</th
      >
      <th
        scope="col"
        class="px-3 py-3.5 text-right text-sm font-semibold text-gray-900 dark:text-gray-100"
        >P99</th
      >
      <th
        scope="col"
        class="px-3 py-3.5 text-right text-sm font-semibold text-gray-900 dark:text-gray-100"
        >Max</th
      >
    </tr>
  {/snippet}

  {#each runs as run (run.concurrency)}
    <tr>
      <td
        class="py-2 pr-3 pl-4 text-sm font-medium whitespace-nowrap text-gray-900 sm:pl-0 dark:text-gray-100"
        >{run.concurrency}</td
      >
      <td class="px-3 py-2 text-right text-sm whitespace-nowrap text-gray-500 dark:text-gray-400"
        >{num(run.iops)}</td
      >
      <td class="px-3 py-2 text-right text-sm whitespace-nowrap text-gray-500 dark:text-gray-400"
        >{throughput(run.throughput)}</td
      >
      <td class="px-3 py-2 text-right text-sm whitespace-nowrap text-gray-500 dark:text-gray-400"
        >{micros(run.latencyP50)}</td
      >
      <td class="px-3 py-2 text-right text-sm whitespace-nowrap text-gray-500 dark:text-gray-400"
        >{micros(run.latencyP95)}</td
      >
      <td class="px-3 py-2 text-right text-sm whitespace-nowrap text-gray-500 dark:text-gray-400"
        >{micros(run.latencyP99)}</td
      >
      <td class="px-3 py-2 text-right text-sm whitespace-nowrap text-gray-500 dark:text-gray-400"
        >{micros(run.latencyMax)}</td
      >
    </tr>
  {/each}
</Table>
