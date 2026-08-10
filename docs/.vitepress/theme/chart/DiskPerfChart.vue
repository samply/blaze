<script setup lang="ts">
import { computed } from "vue";
import ChartFrame from "./ChartFrame.vue";
import LineSeries from "./LineSeries.vue";
import { required } from "./data";
import {
  type DiskPerf,
  formatMicros,
  formatRate,
  referenceIops,
  type ReadRun,
} from "./disk-perf";
import {
  axis,
  type Curve,
  gutter,
  HEIGHT,
  logAxis,
  place,
  scale,
  tick,
  ticks,
  WIDTH,
} from "./plot";

const props = withDefaults(
  defineProps<{
    /** Measurement result, read by the page's data loader. */
    data: DiskPerf;
    title: string;
    /** The plotted metric of the random read sweep. */
    metric?: "iops" | "latency";
  }>(),
  { metric: "iops" },
);

interface Series {
  name: string;
  color: string;
  /** The reference curve is dashed, so it reads as a target, not a measurement. */
  dashed?: boolean;
  value: (run: ReadRun) => number;
}

// The plotted latency percentiles are the ones the score is about. The maximum
// latency is left out because its outliers would compress them all into the
// bottom of the chart.
const SERIES: Record<"iops" | "latency", Series[]> = {
  iops: [
    {
      name: "Measured",
      color: "var(--blaze-chart-line-y1)",
      value: (run) => run.iops,
    },
    {
      name: "Reference",
      color: "var(--blaze-chart-line-reference)",
      dashed: true,
      value: (run) => referenceIops(run.concurrency),
    },
  ],
  latency: [
    {
      name: "P50",
      color: "var(--blaze-chart-line-y2-1)",
      value: (run) => run.latencyP50,
    },
    {
      name: "P95",
      color: "var(--blaze-chart-line-y2-2)",
      value: (run) => run.latencyP95,
    },
    {
      name: "P99",
      color: "var(--blaze-chart-line-y2-3)",
      value: (run) => run.latencyP99,
    },
  ],
};

const X_LABEL = "Concurrent Readers";

const series = computed(() => SERIES[props.metric]);
const runs = computed(() => required(props.data, props.title).readRuns);
const yLabel = computed(() =>
  props.metric === "iops" ? "IOPS" : "Latency (µs)",
);

// The concurrency is swept in powers of two, so it carries a log axis whose
// ticks are exactly the levels that were run.
const xAxis = computed(() => {
  const concurrencies = runs.value.map((run) => run.concurrency);
  return logAxis(
    Math.min(...concurrencies),
    Math.max(...concurrencies),
    concurrencies,
  );
});

const yAxis = computed(() => {
  const values = runs.value.flatMap((run) =>
    series.value.map((series) => series.value(run)),
  );
  return axis(Math.min(...values), Math.max(...values), 0, null, 6);
});

// Above 10,000 the IOPS axis is labelled in thousands, so its ticks stay as
// short as the latency ones.
const yTicks = computed(() => {
  const thousands = props.metric === "iops" && yAxis.value.max >= 10000;
  return ticks(yAxis.value, (value) =>
    thousands ? tick(value / 1000, "k") : tick(value),
  );
});

const xTicks = computed(() => ticks(xAxis.value));

/** Formats a value of the plotted metric for the marker tooltips. */
function format(value: number): string {
  return props.metric === "iops"
    ? `${formatRate(value)} IOPS`
    : formatMicros(value);
}

const plot = computed(() => ({
  top: 30,
  left: gutter(yTicks.value, yLabel.value),
  right: WIDTH - 16,
  bottom: HEIGHT - 50,
}));

const x = computed(() =>
  scale(xAxis.value, plot.value.left, plot.value.right, true),
);
const y = computed(() => scale(yAxis.value, plot.value.bottom, plot.value.top));

const curves = computed<Curve[]>(() =>
  series.value.map((series) => ({
    name: series.name,
    color: series.color,
    dashed: series.dashed,
    points: runs.value.map((run) => ({
      cx: x.value(run.concurrency),
      cy: y.value(series.value(run)),
      label: `${X_LABEL} ${run.concurrency} · ${series.name}: ${format(series.value(run))}`,
    })),
  })),
);

const legend = computed(() =>
  curves.value.map((curve) => ({
    name: curve.name,
    color: curve.color,
    shape: "line" as const,
    dashed: curve.dashed,
  })),
);

const description = computed(() =>
  [
    `Line chart. ${props.title}.`,
    `${X_LABEL} from ${tick(xAxis.value.min)} to ${tick(xAxis.value.max)}.`,
    `${yLabel.value}: ${series.value.map((series) => series.name).join(", ")}.`,
  ].join(" "),
);
</script>

<template>
  <ChartFrame
    :title="title"
    :description="description"
    :plot="plot"
    :x-ticks="place(xTicks, x)"
    :y-ticks="place(yTicks, y)"
    :x-label="X_LABEL"
    :y-label="yLabel"
    :legend="legend"
  >
    <LineSeries :curves="curves" />
  </ChartFrame>
</template>
