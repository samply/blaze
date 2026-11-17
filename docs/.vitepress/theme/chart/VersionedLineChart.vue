<script setup lang="ts">
import { computed, ref } from "vue";
import LineChart from "./LineChart.vue";
import { cell, list, rows } from "./data";
import {
  assertVersion,
  inVersionOrder,
  select,
  sortVersions,
} from "./versions";

const props = defineProps<{
  /** Directory of the result files `<version>.csv`, relative to `docs/performance`. */
  dir: string;
  title: string;
  xLog?: boolean;
  xMin?: number | null;
  xMax?: number | null;
  xTicks?: number[] | null;
}>();

const CSV = ".csv";
const files = list(props.dir);
const versions = sortVersions(files.map((file) => file.slice(0, -CSV.length)));
for (const version of versions) {
  const file = `${props.dir}/${version}${CSV}`;
  assertVersion(
    file,
    version,
    rows(file)
      .slice(1)
      .map((row) => cell(row, 6)),
  );
}

// The newest version is selected initially, so the static HTML shows it.
const selected = ref([versions[versions.length - 1]]);
const shown = computed(() => inVersionOrder(selected.value, versions));
</script>

<template>
  <div class="versioned-chart">
    <div
      class="version-buttons"
      role="group"
      :aria-label="`Versions of ${title}`"
    >
      <button
        v-for="version in versions"
        :key="version"
        type="button"
        :aria-pressed="selected.includes(version)"
        @click="selected = select(selected, version)"
      >
        {{ version }}
      </button>
    </div>
    <LineChart
      :src="shown.map((version) => `${dir}/${version}${CSV}`)"
      :src-labels="shown"
      :title="`${title} · ${shown.join(' vs ')}`"
      :x-log="xLog"
      :x-min="xMin"
      :x-max="xMax"
      :x-ticks="xTicks"
    />
  </div>
</template>
