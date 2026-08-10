import { defineLoader } from "vitepress";
import { loadDiskPerf } from "../.vitepress/loader/disk-perf";
import type { DiskPerf } from "../.vitepress/theme/chart/disk-perf";

// The files are listed one by one rather than matched by a glob, so that a
// page ships exactly the data it plots.
export declare const data: Record<string, DiskPerf>;

export default defineLoader({
  watch: [
    "./disk-perf/A5N46.json",
    "./disk-perf/LEA47.json",
    "./disk-perf/LEA79.json",
  ],
  load: loadDiskPerf,
});
