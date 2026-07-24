<script lang="ts">
  import type { RandReadRun } from '$lib/jobs/disk-perf';
  import prettyMicros from '$lib/pretty-micros';

  interface Props {
    /** runs of the random read sweep, ordered by ascending concurrency */
    runs: RandReadRun[];
  }

  let { runs }: Props = $props();

  const width = 640;
  const height = 280;
  const margin = { top: 12, right: 16, bottom: 40, left: 56 };
  const plotWidth = width - margin.left - margin.right;
  const plotHeight = height - margin.top - margin.bottom;

  // the plotted percentiles, ordered from lowest to highest, each with its own
  // color; the maximum latency is left out because its outliers would compress
  // the percentiles — it stays in the table
  const series: {
    label: string;
    value: (run: RandReadRun) => number | undefined;
    stroke: string;
    fill: string;
  }[] = [
    {
      label: 'P50',
      value: (run) => run.latencyP50,
      stroke: 'stroke-indigo-600 dark:stroke-indigo-400',
      fill: 'fill-indigo-600 dark:fill-indigo-400'
    },
    {
      label: 'P95',
      value: (run) => run.latencyP95,
      stroke: 'stroke-amber-500 dark:stroke-amber-400',
      fill: 'fill-amber-500 dark:fill-amber-400'
    },
    {
      label: 'P99',
      value: (run) => run.latencyP99,
      stroke: 'stroke-rose-600 dark:stroke-rose-400',
      fill: 'fill-rose-600 dark:fill-rose-400'
    }
  ];

  /** rounds up to 1, 2 or 5 times a power of ten */
  function niceCeil(value: number): number {
    if (value <= 0) return 1;
    const power = Math.pow(10, Math.floor(Math.log10(value)));
    const fraction = value / power;
    return (fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 5 ? 5 : 10) * power;
  }

  // a run contributes to the chart if it has at least one plotted percentile
  let points = $derived(runs.filter((run) => series.some((s) => s.value(run) !== undefined)));
  let maxLatency = $derived(
    niceCeil(
      Math.max(
        1,
        ...points.flatMap((run) =>
          series.flatMap((s) => {
            const value = s.value(run);
            return value === undefined ? [] : [value];
          })
        )
      )
    )
  );

  // one evenly spaced slot per run; the power-of-two concurrencies make this
  // a log₂ scaled axis
  function x(index: number): number {
    return (
      margin.left + (points.length < 2 ? plotWidth / 2 : (index * plotWidth) / (points.length - 1))
    );
  }

  function y(latency: number): number {
    return margin.top + plotHeight - (latency / maxLatency) * plotHeight;
  }

  let yTicks = $derived([0, 0.25, 0.5, 0.75, 1].map((fraction) => fraction * maxLatency));

  function linePoints(value: (run: RandReadRun) => number | undefined): string {
    return points
      .flatMap((run, i) => {
        const latency = value(run);
        return latency === undefined ? [] : [`${x(i)},${y(latency)}`];
      })
      .join(' ');
  }
</script>

{#if points.length > 0}
  <div class="mt-4 max-w-2xl">
    <div class="mb-1 flex gap-4">
      {#each series as s (s.label)}
        <span class="flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">
          <svg width="16" height="4" aria-hidden="true">
            <line x1="0" y1="2" x2="16" y2="2" stroke-width="2" class={s.stroke} />
          </svg>
          {s.label}
        </span>
      {/each}
    </div>
    <svg
      viewBox="0 0 {width} {height}"
      class="w-full"
      role="img"
      aria-label="Random read latency percentiles per concurrency"
    >
      {#each yTicks as tick (tick)}
        <line
          x1={margin.left}
          y1={y(tick)}
          x2={width - margin.right}
          y2={y(tick)}
          stroke-width="1"
          class="stroke-gray-200 dark:stroke-gray-600"
        />
        <text
          x={margin.left - 8}
          y={y(tick)}
          text-anchor="end"
          dominant-baseline="middle"
          class="fill-gray-500 dark:fill-gray-400 text-[11px]">{prettyMicros(tick)}</text
        >
      {/each}
      {#each points as run, i (run.concurrency)}
        <text
          x={x(i)}
          y={height - margin.bottom + 16}
          text-anchor="middle"
          class="fill-gray-500 dark:fill-gray-400 text-[11px]">{run.concurrency}</text
        >
      {/each}
      <text
        x={margin.left + plotWidth / 2}
        y={height - 4}
        text-anchor="middle"
        class="fill-gray-500 dark:fill-gray-400 text-[11px]">Concurrency</text
      >
      {#each series as s (s.label)}
        <polyline points={linePoints(s.value)} fill="none" stroke-width="2" class={s.stroke} />
      {/each}
      {#each series as s (s.label)}
        {#each points as run, i (run.concurrency)}
          {@const latency = s.value(run)}
          {#if latency !== undefined}
            <circle cx={x(i)} cy={y(latency)} r="3.5" class={s.fill}>
              <title>{s.label} {prettyMicros(latency)} at concurrency {run.concurrency}</title>
            </circle>
          {/if}
        {/each}
      {/each}
    </svg>
    <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
      Random read latency percentiles per concurrency. The maximum latency is omitted here to keep
      the percentiles readable; it remains in the table below.
    </p>
  </div>
{/if}
