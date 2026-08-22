<script setup lang="ts">
import { computed } from "vue";
import ChartFrame from "./ChartFrame.vue";
import LineSeries from "./LineSeries.vue";
import { num, required, type Row } from "./data";
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
    /** Rows of the data file, read by the page's data loader. */
    data: Row[];
    title: string;
    xLabel?: string;
    /** 1-based column holding the x value. */
    xCol?: number;
    xLog?: boolean;
    xMin?: number | null;
    xMax?: number | null;
    /** Explicit x ticks, required on a log axis. */
    xTicks?: number[] | null;
    yLabel?: string;
    /** 1-based columns of the series carried by the left axis. */
    yCols?: number[];
    ySeries?: string[];
    yMin?: number | null;
    yMax?: number | null;
    y2Label?: string;
    /** 1-based columns of the series carried by the right axis. */
    y2Cols?: number[];
    y2Series?: string[];
    y2Min?: number | null;
    y2Max?: number | null;
  }>(),
  {
    xLabel: "Concurrent Clients",
    xCol: 1,
    xLog: false,
    xMin: null,
    xMax: null,
    xTicks: null,
    yLabel: "Requests/s",
    yCols: () => [2],
    ySeries: () => ["Requests/s"],
    yMin: 0,
    yMax: null,
    y2Label: "Processing Time (ms)",
    y2Cols: () => [3, 4, 5],
    y2Series: () => ["Median RT", "P95 RT", "P99 RT"],
    y2Min: 0,
    y2Max: null,
  },
);

const plotted = computed(() => {
  const all = required(props.data, props.title);
  const values = (col: number) =>
    all.map((row) => ({ x: num(row, props.xCol), y: num(row, col) }));
  return {
    x: all.map((row) => num(row, props.xCol)),
    y: props.yCols.map(values),
    y2: props.y2Cols.map(values),
  };
});

const xAxis = computed(() => {
  if (props.xTicks) {
    return logAxis(
      props.xMin ?? Math.min(...plotted.value.x),
      props.xMax ?? Math.max(...plotted.value.x),
      props.xTicks,
    );
  }
  if (props.xLog) {
    throw new Error("a log x axis needs an explicit x-ticks list");
  }
  return axis(
    Math.min(...plotted.value.x),
    Math.max(...plotted.value.x),
    props.xMin,
    props.xMax,
    8,
  );
});

const yAxis = computed(() => {
  const values = plotted.value.y.flat().map((point) => point.y);
  return axis(
    Math.min(...values),
    Math.max(...values),
    props.yMin,
    props.yMax,
    6,
  );
});

// The right axis aims for the number of intervals the left axis ended up with,
// so both scales tend to land on the same gridlines instead of interleaving
// two sets of horizontal lines.
const y2Axis = computed(() => {
  const values = plotted.value.y2.flat().map((point) => point.y);
  return axis(
    Math.min(...values),
    Math.max(...values),
    props.y2Min,
    props.y2Max,
    yAxis.value.ticks.length - 1,
  );
});

const xAxisTicks = computed(() => ticks(xAxis.value));
const yTicks = computed(() => ticks(yAxis.value));
const y2Ticks = computed(() => ticks(y2Axis.value));

const plot = computed(() => ({
  top: 30,
  left: gutter(yTicks.value, props.yLabel),
  right: WIDTH - gutter(y2Ticks.value, props.y2Label),
  bottom: HEIGHT - (props.xLabel ? 50 : 26),
}));

const x = computed(() =>
  scale(xAxis.value, plot.value.left, plot.value.right, props.xLog),
);
const y = computed(() => scale(yAxis.value, plot.value.bottom, plot.value.top));
const y2 = computed(() =>
  scale(y2Axis.value, plot.value.bottom, plot.value.top),
);

const curves = computed<Curve[]>(() => {
  const of = (
    series: { x: number; y: number }[][],
    names: string[],
    project: (value: number) => number,
    color: (index: number) => string,
    unit: string,
  ) =>
    series.map((points, index) => ({
      name: names[index] ?? `Series ${index + 1}`,
      color: color(index),
      points: points.map((point) => ({
        cx: x.value(point.x),
        cy: project(point.y),
        label: `${props.xLabel} ${tick(point.x)} · ${names[index]}: ${tick(point.y)}${unit}`,
      })),
    }));
  return [
    ...of(plotted.value.y, props.ySeries, y.value, () => "var(--blaze-chart-line-y1)", ""),
    ...of(
      plotted.value.y2,
      props.y2Series,
      y2.value,
      (index) => `var(--blaze-chart-line-y2-${Math.min(index + 1, 3)})`,
      " ms",
    ),
  ];
});

const legend = computed(() =>
  curves.value.map((curve) => ({
    name: curve.name,
    color: curve.color,
    shape: "line" as const,
  })),
);

const description = computed(() =>
  [
    `Line chart. ${props.title}.`,
    `${props.xLabel} from ${tick(xAxis.value.min)} to ${tick(xAxis.value.max)}.`,
    `Left axis ${props.yLabel}: ${props.ySeries.join(", ")}.`,
    `Right axis ${props.y2Label}: ${props.y2Series.join(", ")}.`,
  ].join(" "),
);
</script>

<template>
  <ChartFrame
    :title="title"
    :description="description"
    :plot="plot"
    :x-ticks="place(xAxisTicks, x)"
    :y-ticks="place(yTicks, y)"
    :y2-ticks="place(y2Ticks, y2)"
    right-axis
    :x-label="xLabel"
    :y-label="yLabel"
    :y2-label="y2Label"
    :legend="legend"
  >
    <LineSeries :curves="curves" />
  </ChartFrame>
</template>
