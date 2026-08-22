<script setup lang="ts">
import { computed } from "vue";
import ChartFrame from "./ChartFrame.vue";
import { categories, num, required, type Row, series } from "./data";
import {
  axis,
  bar,
  gutter,
  HEIGHT,
  place,
  type PlacedTick,
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
    /** Series names, in the order the rows are interleaved in `data`. */
    series?: string[];
    xLabel?: string;
    /** 1-based column holding the category, as in gnuplot's `xtic(n)`. */
    xCol: number;
    yLabel?: string;
    /** 1-based column holding the value, as in gnuplot's `using n`. */
    yCol: number;
    /** Bar baseline. Truncating it overstates the differences between bars. */
    yMin?: number | null;
    yMax?: number | null;
    /**
     * Unit appended to the y tick labels. Defaults to the `k` of the
     * `set format y "%.0f k"` every replaced gnuplot script used.
     */
    ySuffix?: string;
  }>(),
  { series: () => [], yMin: 0, yMax: null, ySuffix: "k" },
);

// Bars wider than this read as blocks rather than as a comparison of lengths.
const MAX_BAR_WIDTH = 24;

// Fraction of a category slot the cluster of bars takes up, gnuplot's
// `set boxwidth 0.8`.
const CLUSTER_WIDTH = 0.8;

// Surface-colored gap between adjacent bars, so a cluster reads as separate
// bars instead of one striped block.
const BAR_GAP = 2;

const names = computed(() =>
  props.series.length > 0 ? props.series : [props.title],
);

const plotted = computed(() => {
  const all = series(required(props.data, props.title), names.value.length);
  return {
    categories: categories(all, props.xCol),
    values: all.map((rows) => rows.map((row) => num(row, props.yCol))),
  };
});

const yAxis = computed(() => {
  const values = plotted.value.values.flat();
  return axis(
    Math.min(...values),
    Math.max(...values),
    props.yMin,
    props.yMax,
    6,
  );
});

const yTicks = computed(() =>
  ticks(yAxis.value, (value) => tick(value, props.ySuffix)),
);

// A single series carries its name as a subtitle rather than as a one-entry
// legend box.
const subtitle = computed(() =>
  props.series.length === 1 ? props.series[0] : null,
);

const plot = computed(() => ({
  top: subtitle.value ? 46 : 30,
  left: gutter(yTicks.value, props.yLabel),
  right: WIDTH - 16,
  bottom: HEIGHT - (props.xLabel ? 50 : 26),
}));

const y = computed(() =>
  scale(yAxis.value, plot.value.bottom, plot.value.top),
);

const baseline = computed(() => y.value(Math.max(yAxis.value.min, 0)));

const color = (index: number) =>
  `var(--blaze-chart-bar-${names.value.length}-${index + 1})`;

const bars = computed(() => {
  const { left, right } = plot.value;
  const slot = (right - left) / plotted.value.categories.length;
  const width = Math.min(MAX_BAR_WIDTH, (slot * CLUSTER_WIDTH) / names.value.length);
  const cluster = width * names.value.length;
  return plotted.value.values.flatMap((values, seriesIndex) =>
    values.map((value, categoryIndex) => ({
      key: `${seriesIndex}-${categoryIndex}`,
      color: color(seriesIndex),
      path: bar(
        left +
          slot * categoryIndex +
          (slot - cluster) / 2 +
          width * seriesIndex +
          BAR_GAP / 2,
        Math.max(1, width - BAR_GAP),
        baseline.value,
        y.value(value),
      ),
      label: `${plotted.value.categories[categoryIndex]} · ${names.value[seriesIndex]}: ${tick(value, props.ySuffix)}`,
    })),
  );
});

// The categories sit at the centre of their slot, so they are placed here
// rather than by a scale.
const xTicks = computed<PlacedTick[]>(() => {
  const { left, right } = plot.value;
  const slot = (right - left) / plotted.value.categories.length;
  return plotted.value.categories.map((label, i) => ({
    label,
    pos: left + slot * (i + 0.5),
  }));
});

const legend = computed(() =>
  props.series.length > 1
    ? props.series.map((name, index) => ({
        name,
        color: color(index),
        shape: "rect" as const,
      }))
    : [],
);

const description = computed(() =>
  [
    `Bar chart. ${props.title}.`,
    `${props.xLabel ?? "Category"}: ${plotted.value.categories.join(", ")}.`,
    `${props.yLabel ?? "Value"}${subtitle.value ? ` for ${subtitle.value}` : ""}.`,
    props.series.length > 1
      ? `Series: ${props.series.join(", ")}.`
      : "",
  ]
    .filter(Boolean)
    .join(" "),
);
</script>

<template>
  <ChartFrame
    :title="title"
    :subtitle="subtitle"
    :description="description"
    :plot="plot"
    :x-ticks="xTicks"
    :y-ticks="place(yTicks, y)"
    :x-grid="false"
    :baseline="baseline"
    :left-axis="false"
    :x-label="xLabel"
    :y-label="yLabel"
    :legend="legend"
  >
    <path v-for="b in bars" :key="b.key" :d="b.path" :fill="b.color">
      <title>{{ b.label }}</title>
    </path>
  </ChartFrame>
</template>
