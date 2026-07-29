<script setup lang="ts">
import { computed } from "vue";
import {
  bestReadRun,
  diskPerf,
  formatMicros,
  formatRate,
  formatThroughput,
} from "./disk-perf";

const props = defineProps<{
  /** Measurement result file, relative to `docs/performance`. */
  src: string;
}>();

const stats = computed(() => {
  const result = diskPerf(props.src);
  const best = bestReadRun(result);
  return [
    {
      label: "Score",
      value: result.score.toFixed(1),
      note: `/ 100 · ${result.rating}`,
    },
    {
      label: "Best Random Read IOPS",
      value: formatRate(best.iops),
      note: `@ ${best.concurrency} readers`,
    },
    {
      label: "Sequential Write Throughput",
      value: formatThroughput(result.seqWriteThroughput),
    },
    {
      label: "Fsyncs per Second",
      value: formatRate(result.fsyncRate),
      note: `· P50 ${formatMicros(result.fsyncLatencyP50)}`,
    },
  ];
});
</script>

<template>
  <dl class="blaze-stats">
    <div v-for="stat in stats" :key="stat.label">
      <dt>{{ stat.label }}</dt>
      <dd>
        {{ stat.value }}<span v-if="stat.note">{{ stat.note }}</span>
      </dd>
    </div>
  </dl>
</template>
