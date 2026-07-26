<script setup lang="ts">
import { computed } from "vue";
import { categories, num, rows, series } from "./data";
import {
  axis,
  bar,
  FONT_SIZE,
  HEIGHT,
  scale,
  textWidth,
  tick,
  TITLE_FONT_SIZE,
  WIDTH,
} from "./plot";

const props = withDefaults(
  defineProps<{
    /** Data file, relative to `docs/performance`. */
    src: string;
    title: string;
    /** Series names, in the order the rows are interleaved in `src`. */
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

const data = computed(() => {
  const all = series(rows(props.src), names.value.length);
  return {
    categories: categories(all, props.xCol),
    values: all.map((rows) => rows.map((row) => num(row, props.yCol))),
  };
});

const yAxis = computed(() => {
  const values = data.value.values.flat();
  return axis(
    Math.min(...values),
    Math.max(...values),
    props.yMin,
    props.yMax,
    6,
  );
});

const yTicks = computed(() =>
  yAxis.value.ticks.map((value) => ({
    value,
    label: tick(value, props.ySuffix),
  })),
);

// A single series carries its name as a subtitle rather than as a one-entry
// legend box.
const subtitle = computed(() =>
  props.series.length === 1 ? props.series[0] : null,
);

const legend = computed(() =>
  props.series.length > 1
    ? props.series.map((name, i) => ({ name, index: i }))
    : [],
);

const plot = computed(() => {
  const top = subtitle.value ? 46 : 30;
  const left =
    (props.yLabel ? 26 : 8) +
    Math.max(...yTicks.value.map((tick) => textWidth(tick.label))) +
    8;
  const bottom = HEIGHT - (props.xLabel ? 50 : 26);
  return { top, left, right: WIDTH - 16, bottom };
});

const y = computed(() =>
  scale(yAxis.value, plot.value.bottom, plot.value.top),
);

const bars = computed(() => {
  const { left, right, bottom } = plot.value;
  const slot = (right - left) / data.value.categories.length;
  const width = Math.min(MAX_BAR_WIDTH, (slot * CLUSTER_WIDTH) / names.value.length);
  const cluster = width * names.value.length;
  const baseline = y.value(Math.max(yAxis.value.min, 0));
  return data.value.values.flatMap((values, seriesIndex) =>
    values.map((value, categoryIndex) => ({
      key: `${seriesIndex}-${categoryIndex}`,
      series: seriesIndex,
      path: bar(
        left +
          slot * categoryIndex +
          (slot - cluster) / 2 +
          width * seriesIndex +
          BAR_GAP / 2,
        Math.max(1, width - BAR_GAP),
        baseline,
        y.value(value),
      ),
      label: `${data.value.categories[categoryIndex]} · ${names.value[seriesIndex]}: ${tick(value, props.ySuffix)}`,
    })),
  );
});

const xTicks = computed(() => {
  const { left, right } = plot.value;
  const slot = (right - left) / data.value.categories.length;
  return data.value.categories.map((label, i) => ({
    label,
    x: left + slot * (i + 0.5),
  }));
});

const color = (index: number) =>
  `var(--blaze-chart-bar-${names.value.length}-${index + 1})`;

const description = computed(() =>
  [
    `Bar chart. ${props.title}.`,
    `${props.xLabel ?? "Category"}: ${data.value.categories.join(", ")}.`,
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
  <figure class="blaze-chart">
    <svg
      :viewBox="`0 0 ${WIDTH} ${HEIGHT}`"
      role="img"
      :aria-label="description"
    >
      <desc>{{ description }}</desc>

      <text
        class="blaze-chart-title"
        :x="(plot.left + plot.right) / 2"
        y="20"
        :font-size="TITLE_FONT_SIZE"
      >
        {{ title }}
      </text>
      <text
        v-if="subtitle"
        class="blaze-chart-subtitle"
        :x="(plot.left + plot.right) / 2"
        y="38"
        :font-size="FONT_SIZE"
      >
        {{ subtitle }}
      </text>

      <g class="blaze-chart-grid">
        <line
          v-for="t in yTicks"
          :key="t.value"
          :x1="plot.left"
          :x2="plot.right"
          :y1="y(t.value)"
          :y2="y(t.value)"
        />
      </g>

      <g class="blaze-chart-tick" :font-size="FONT_SIZE">
        <text
          v-for="t in yTicks"
          :key="t.value"
          :x="plot.left - 8"
          :y="y(t.value)"
          text-anchor="end"
          dominant-baseline="middle"
        >
          {{ t.label }}
        </text>
        <text
          v-for="t in xTicks"
          :key="t.label"
          :x="t.x"
          :y="plot.bottom + 18"
          text-anchor="middle"
        >
          {{ t.label }}
        </text>
      </g>

      <line
        class="blaze-chart-axis"
        :x1="plot.left"
        :x2="plot.right"
        :y1="y(Math.max(yAxis.min, 0))"
        :y2="y(Math.max(yAxis.min, 0))"
      />

      <path
        v-for="b in bars"
        :key="b.key"
        :d="b.path"
        :fill="color(b.series)"
      >
        <title>{{ b.label }}</title>
      </path>

      <text
        v-if="yLabel"
        class="blaze-chart-label"
        :font-size="FONT_SIZE"
        text-anchor="middle"
        :transform="`translate(14 ${(plot.top + plot.bottom) / 2}) rotate(-90)`"
      >
        {{ yLabel }}
      </text>
      <text
        v-if="xLabel"
        class="blaze-chart-label"
        :font-size="FONT_SIZE"
        text-anchor="middle"
        :x="(plot.left + plot.right) / 2"
        :y="HEIGHT - 10"
      >
        {{ xLabel }}
      </text>

      <g
        v-if="legend.length > 0"
        class="blaze-chart-legend"
        :font-size="FONT_SIZE"
      >
        <g
          v-for="entry in legend"
          :key="entry.name"
          :transform="`translate(${plot.left + 14} ${plot.top + 10 + entry.index * 19})`"
        >
          <rect width="13" height="13" rx="2" :fill="color(entry.index)" />
          <text x="19" y="10">{{ entry.name }}</text>
        </g>
      </g>
    </svg>
  </figure>
</template>
