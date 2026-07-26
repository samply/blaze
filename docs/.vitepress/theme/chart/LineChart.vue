<script setup lang="ts">
import { computed } from "vue";
import { num, rows } from "./data";
import {
  axis,
  FONT_SIZE,
  HEIGHT,
  logAxis,
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

// Radius of a data point marker. It carries a surface-colored ring, so points
// of different series stay readable where the curves cross.
const MARKER_RADIUS = 4;

const data = computed(() => {
  const all = rows(props.src);
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
      props.xMin ?? Math.min(...data.value.x),
      props.xMax ?? Math.max(...data.value.x),
      props.xTicks,
    );
  }
  if (props.xLog) {
    throw new Error("a log x axis needs an explicit x-ticks list");
  }
  return axis(
    Math.min(...data.value.x),
    Math.max(...data.value.x),
    props.xMin,
    props.xMax,
    8,
  );
});

const yAxis = computed(() => {
  const values = data.value.y.flat().map((point) => point.y);
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
  const values = data.value.y2.flat().map((point) => point.y);
  return axis(
    Math.min(...values),
    Math.max(...values),
    props.y2Min,
    props.y2Max,
    yAxis.value.ticks.length - 1,
  );
});

const plot = computed(() => {
  const width = (values: number[]) =>
    Math.max(...values.map((value) => textWidth(tick(value))));
  return {
    top: 30,
    left: (props.yLabel ? 26 : 8) + width(yAxis.value.ticks) + 8,
    right:
      WIDTH - (props.y2Label ? 26 : 8) - width(y2Axis.value.ticks) - 8,
    bottom: HEIGHT - (props.xLabel ? 50 : 26),
  };
});

const x = computed(() =>
  scale(xAxis.value, plot.value.left, plot.value.right, props.xLog),
);
const y = computed(() => scale(yAxis.value, plot.value.bottom, plot.value.top));
const y2 = computed(() =>
  scale(y2Axis.value, plot.value.bottom, plot.value.top),
);

const curves = computed(() => {
  const of = (
    points: { x: number; y: number }[][],
    names: string[],
    project: (value: number) => number,
    color: (index: number) => string,
    unit: string,
  ) =>
    points.map((points, index) => ({
      name: names[index] ?? `Series ${index + 1}`,
      color: color(index),
      path: points
        .map(
          (point, i) =>
            `${i === 0 ? "M" : "L"}${x.value(point.x)} ${project(point.y)}`,
        )
        .join(""),
      points: points.map((point) => ({
        cx: x.value(point.x),
        cy: project(point.y),
        label: `${props.xLabel} ${tick(point.x)} · ${names[index]}: ${tick(point.y)}${unit}`,
      })),
    }));
  return [
    ...of(data.value.y, props.ySeries, y.value, () => "var(--blaze-chart-line-y1)", ""),
    ...of(
      data.value.y2,
      props.y2Series,
      y2.value,
      (index) => `var(--blaze-chart-line-y2-${Math.min(index + 1, 3)})`,
      " ms",
    ),
  ];
});

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

      <g class="blaze-chart-grid">
        <line
          v-for="t in yAxis.ticks"
          :key="`y${t}`"
          :x1="plot.left"
          :x2="plot.right"
          :y1="y(t)"
          :y2="y(t)"
        />
        <line
          v-for="t in xAxis.ticks"
          :key="`x${t}`"
          :x1="x(t)"
          :x2="x(t)"
          :y1="plot.top"
          :y2="plot.bottom"
        />
      </g>

      <g class="blaze-chart-tick" :font-size="FONT_SIZE">
        <text
          v-for="t in yAxis.ticks"
          :key="`y${t}`"
          :x="plot.left - 8"
          :y="y(t)"
          text-anchor="end"
          dominant-baseline="middle"
        >
          {{ tick(t) }}
        </text>
        <text
          v-for="t in y2Axis.ticks"
          :key="`y2${t}`"
          :x="plot.right + 8"
          :y="y2(t)"
          text-anchor="start"
          dominant-baseline="middle"
        >
          {{ tick(t) }}
        </text>
        <text
          v-for="t in xAxis.ticks"
          :key="`x${t}`"
          :x="x(t)"
          :y="plot.bottom + 18"
          text-anchor="middle"
        >
          {{ tick(t) }}
        </text>
      </g>

      <g class="blaze-chart-axis">
        <line
          :x1="plot.left"
          :x2="plot.right"
          :y1="plot.bottom"
          :y2="plot.bottom"
        />
        <line
          :x1="plot.left"
          :x2="plot.left"
          :y1="plot.top"
          :y2="plot.bottom"
        />
        <line
          :x1="plot.right"
          :x2="plot.right"
          :y1="plot.top"
          :y2="plot.bottom"
        />
      </g>

      <g v-for="curve in curves" :key="curve.name">
        <path class="blaze-chart-line" :d="curve.path" :stroke="curve.color" />
        <circle
          v-for="(point, i) in curve.points"
          :key="i"
          class="blaze-chart-marker"
          :cx="point.cx"
          :cy="point.cy"
          :r="MARKER_RADIUS"
          :fill="curve.color"
        >
          <title>{{ point.label }}</title>
        </circle>
      </g>

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
        v-if="y2Label"
        class="blaze-chart-label"
        :font-size="FONT_SIZE"
        text-anchor="middle"
        :transform="`translate(${WIDTH - 10} ${(plot.top + plot.bottom) / 2}) rotate(90)`"
      >
        {{ y2Label }}
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

      <g class="blaze-chart-legend" :font-size="FONT_SIZE">
        <g
          v-for="(curve, index) in curves"
          :key="curve.name"
          :transform="`translate(${plot.left + 14} ${plot.top + 10 + index * 19})`"
        >
          <line
            class="blaze-chart-line"
            x1="0"
            y1="6"
            x2="16"
            y2="6"
            :stroke="curve.color"
          />
          <circle
            class="blaze-chart-marker"
            cx="8"
            cy="6"
            :r="MARKER_RADIUS"
            :fill="curve.color"
          />
          <text x="24" y="7">{{ curve.name }}</text>
        </g>
      </g>
    </svg>
  </figure>
</template>
