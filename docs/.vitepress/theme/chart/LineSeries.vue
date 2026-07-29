<script setup lang="ts">
// The curves of a line chart, drawn into the plot rectangle of a `ChartFrame`.
import { type Curve, linePath, MARKER_RADIUS } from "./plot";

defineProps<{ curves: Curve[] }>();
</script>

<template>
  <g v-for="curve in curves" :key="curve.name">
    <path
      class="blaze-chart-line"
      :class="{ 'blaze-chart-line-dashed': curve.dashed }"
      :d="linePath(curve.points)"
      :stroke="curve.color"
    />
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
</template>
