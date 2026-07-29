<script setup lang="ts">
// The frame every performance chart draws into: title, gridlines, tick labels,
// axis lines, axis labels and the legend. The marks themselves — curves, bars —
// come from the default slot, which is rendered into the plot rectangle between
// the axis lines and the axis labels.
import {
  FONT_SIZE,
  HEIGHT,
  type LegendEntry,
  MARKER_RADIUS,
  type PlacedTick,
  type Plot,
  TITLE_FONT_SIZE,
  WIDTH,
} from "./plot";

const props = withDefaults(
  defineProps<{
    title: string;
    /** A single-series chart names its series here instead of in a legend. */
    subtitle?: string | null;
    /** Text alternative of the whole chart. */
    description: string;
    plot: Plot;
    xTicks: PlacedTick[];
    yTicks: PlacedTick[];
    /** Ticks of the right axis, for charts that carry a second scale. */
    y2Ticks?: PlacedTick[];
    /**
     * Whether the vertical gridlines of the x ticks are drawn. A category axis
     * has no positions to draw them at.
     */
    xGrid?: boolean;
    /**
     * Position of the horizontal axis line. Defaults to the bottom of the plot;
     * a bar chart puts it on the baseline the bars rise from instead.
     */
    baseline?: number | null;
    leftAxis?: boolean;
    rightAxis?: boolean;
    xLabel?: string | null;
    yLabel?: string | null;
    y2Label?: string | null;
    legend?: LegendEntry[];
  }>(),
  {
    subtitle: null,
    y2Ticks: () => [],
    xGrid: true,
    baseline: null,
    leftAxis: true,
    rightAxis: false,
    xLabel: null,
    yLabel: null,
    y2Label: null,
    legend: () => [],
  },
);

const center = () => (props.plot.left + props.plot.right) / 2;
const middle = () => (props.plot.top + props.plot.bottom) / 2;
const baselineY = () => props.baseline ?? props.plot.bottom;
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
        :x="center()"
        y="20"
        :font-size="TITLE_FONT_SIZE"
      >
        {{ title }}
      </text>
      <text
        v-if="subtitle"
        class="blaze-chart-subtitle"
        :x="center()"
        y="38"
        :font-size="FONT_SIZE"
      >
        {{ subtitle }}
      </text>

      <g class="blaze-chart-grid">
        <line
          v-for="t in yTicks"
          :key="`y${t.pos}`"
          :x1="plot.left"
          :x2="plot.right"
          :y1="t.pos"
          :y2="t.pos"
        />
        <template v-if="xGrid">
          <line
            v-for="t in xTicks"
            :key="`x${t.pos}`"
            :x1="t.pos"
            :x2="t.pos"
            :y1="plot.top"
            :y2="plot.bottom"
          />
        </template>
      </g>

      <g class="blaze-chart-tick" :font-size="FONT_SIZE">
        <text
          v-for="t in yTicks"
          :key="`y${t.pos}`"
          :x="plot.left - 8"
          :y="t.pos"
          text-anchor="end"
          dominant-baseline="middle"
        >
          {{ t.label }}
        </text>
        <text
          v-for="t in y2Ticks"
          :key="`y2${t.pos}`"
          :x="plot.right + 8"
          :y="t.pos"
          text-anchor="start"
          dominant-baseline="middle"
        >
          {{ t.label }}
        </text>
        <text
          v-for="t in xTicks"
          :key="`x${t.pos}`"
          :x="t.pos"
          :y="plot.bottom + 18"
          text-anchor="middle"
        >
          {{ t.label }}
        </text>
      </g>

      <g class="blaze-chart-axis">
        <line
          :x1="plot.left"
          :x2="plot.right"
          :y1="baselineY()"
          :y2="baselineY()"
        />
        <line
          v-if="leftAxis"
          :x1="plot.left"
          :x2="plot.left"
          :y1="plot.top"
          :y2="plot.bottom"
        />
        <line
          v-if="rightAxis"
          :x1="plot.right"
          :x2="plot.right"
          :y1="plot.top"
          :y2="plot.bottom"
        />
      </g>

      <slot />

      <text
        v-if="yLabel"
        class="blaze-chart-label"
        :font-size="FONT_SIZE"
        text-anchor="middle"
        :transform="`translate(14 ${middle()}) rotate(-90)`"
      >
        {{ yLabel }}
      </text>
      <text
        v-if="y2Label"
        class="blaze-chart-label"
        :font-size="FONT_SIZE"
        text-anchor="middle"
        :transform="`translate(${WIDTH - 10} ${middle()}) rotate(90)`"
      >
        {{ y2Label }}
      </text>
      <text
        v-if="xLabel"
        class="blaze-chart-label"
        :font-size="FONT_SIZE"
        text-anchor="middle"
        :x="center()"
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
          v-for="(entry, index) in legend"
          :key="entry.name"
          :transform="`translate(${plot.left + 14} ${plot.top + 10 + index * 19})`"
        >
          <template v-if="entry.shape === 'line'">
            <line
              class="blaze-chart-line"
              :class="{ 'blaze-chart-line-dashed': entry.dashed }"
              x1="0"
              y1="6"
              x2="16"
              y2="6"
              :stroke="entry.color"
            />
            <circle
              class="blaze-chart-marker"
              cx="8"
              cy="6"
              :r="MARKER_RADIUS"
              :fill="entry.color"
            />
            <text x="24" y="7">{{ entry.name }}</text>
          </template>
          <template v-else>
            <rect width="13" height="13" rx="2" :fill="entry.color" />
            <text x="19" y="10">{{ entry.name }}</text>
          </template>
        </g>
      </g>
    </svg>
  </figure>
</template>
