<script lang="ts">
  import type { RandReadRun } from '$lib/jobs/disk-perf';
  import prettyNum from '$lib/pretty-num';

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

  // the reference curve of a good local NVMe SSD the score is computed against
  const referenceIopsPerReader = 10000;
  const referenceMaxConcurrency = 32;

  function referenceIops(concurrency: number): number {
    return referenceIopsPerReader * Math.min(concurrency, referenceMaxConcurrency);
  }

  /** rounds up to 1, 2 or 5 times a power of ten */
  function niceCeil(value: number): number {
    if (value <= 0) return 1;
    const power = Math.pow(10, Math.floor(Math.log10(value)));
    const fraction = value / power;
    return (fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 5 ? 5 : 10) * power;
  }

  let points = $derived(runs.filter((run) => run.iops !== undefined));
  let maxIops = $derived(
    niceCeil(
      Math.max(
        ...points.map((run) => run.iops ?? 0),
        ...points.map((run) => referenceIops(run.concurrency))
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

  function y(iops: number): number {
    return margin.top + plotHeight - (iops / maxIops) * plotHeight;
  }

  let yTicks = $derived([0, 0.25, 0.5, 0.75, 1].map((fraction) => fraction * maxIops));
  let measuredPoints = $derived(points.map((run, i) => `${x(i)},${y(run.iops ?? 0)}`).join(' '));
  let referencePoints = $derived(
    points.map((run, i) => `${x(i)},${y(referenceIops(run.concurrency))}`).join(' ')
  );
</script>

{#if points.length > 0}
  <div class="mt-4 max-w-2xl">
    <svg
      viewBox="0 0 {width} {height}"
      class="w-full"
      role="img"
      aria-label="Random read IOPS per concurrency"
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
          class="fill-gray-500 dark:fill-gray-400 text-[11px]">{prettyNum(tick)}</text
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
      <polyline
        points={referencePoints}
        fill="none"
        stroke-width="1.5"
        stroke-dasharray="4 4"
        class="stroke-gray-400 dark:stroke-gray-500"
      />
      <polyline
        points={measuredPoints}
        fill="none"
        stroke-width="2"
        class="stroke-indigo-600 dark:stroke-indigo-400"
      />
      {#each points as run, i (run.concurrency)}
        <circle
          cx={x(i)}
          cy={y(run.iops ?? 0)}
          r="3.5"
          class="fill-indigo-600 dark:fill-indigo-400"
        >
          <title>{prettyNum(run.iops ?? 0)} IOPS at concurrency {run.concurrency}</title>
        </circle>
      {/each}
    </svg>
    <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
      Random read IOPS per concurrency (solid) compared to the reference of a good local NVMe SSD
      (dashed).
    </p>
  </div>
{/if}
