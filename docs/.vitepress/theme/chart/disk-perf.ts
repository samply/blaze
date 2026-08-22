// The model and the formatting of the disk performance measurement results the
// disk-perf charts are rendered from.
//
// A page hands a chart an already parsed `DiskPerf`, read at build time by
// `.vitepress/loader/disk-perf.ts`. Only the model and the formatting live
// here, because this module is part of the client bundle.

/** One run of the random read sweep. */
export interface ReadRun {
  /** Number of concurrent readers of this run. */
  concurrency: number;
  /** Random read operations per second. */
  iops: number;
  /** Random read throughput in bytes per second. */
  throughput: number;
  /** Random read latency percentiles in microseconds. */
  latencyP50: number;
  latencyP95: number;
  latencyP99: number;
  latencyMax: number;
}

/** The outputs of one disk performance measurement job. */
export interface DiskPerf {
  /** Sequential write throughput in bytes per second. */
  seqWriteThroughput: number;
  /** Runs of the random read sweep, ordered by ascending concurrency. */
  readRuns: ReadRun[];
  /** Write + fsync operations per second. */
  fsyncRate: number;
  /** Write + fsync latency percentiles in microseconds. */
  fsyncLatencyP50: number;
  fsyncLatencyP95: number;
  fsyncLatencyP99: number;
  /** Whether the random reads could bypass the page cache. */
  directIo: boolean;
  /** Overall score between 0 and 100. */
  score: number;
  rating: string;
}

// The reference curve of a good local NVMe SSD the score is computed against:
// 10,000 IOPS per reader, corresponding to a 100 µs read, capped at the
// concurrency the sweep ends at.
//
// Source of truth are `reference-iops-per-reader` and
// `reference-max-concurrency` in `blaze.job.disk-perf.score`. The server, the
// admin UI and the docs are separate build units, so the values can't be
// shared and have to be kept in sync by hand — the admin UI carries them in
// `modules/frontend/src/lib/jobs/disk-perf/read-iops-chart.svelte` and the
// "Score" section of `docs/performance/disk-perf.md` states them in prose.
const REFERENCE_IOPS_PER_READER = 10000;
const REFERENCE_MAX_CONCURRENCY = 32;

/** Returns the IOPS a good local NVMe SSD reaches at `concurrency` readers. */
export function referenceIops(concurrency: number): number {
  return (
    REFERENCE_IOPS_PER_READER * Math.min(concurrency, REFERENCE_MAX_CONCURRENCY)
  );
}

/** Returns the run of the random read sweep with the highest IOPS. */
export function bestReadRun(diskPerf: DiskPerf): ReadRun {
  return diskPerf.readRuns.reduce((best, run) =>
    run.iops > best.iops ? run : best,
  );
}

// Values carry at most one decimal, and none as soon as they reach three
// digits, so a label stays short without suggesting a precision the
// measurement doesn't have.
function significant(value: number): string {
  return value >= 100 ? value.toFixed(0) : value.toFixed(1);
}

/** Formats a rate given per second, switching to thousands at 1000/s. */
export function formatRate(value: number): string {
  return value < 1000 ? significant(value) : `${significant(value / 1000)} k`;
}

/** Formats a latency given in microseconds, switching to milliseconds at 1000 µs. */
export function formatMicros(micros: number): string {
  return micros < 1000
    ? `${significant(micros)} µs`
    : `${significant(micros / 1000)} ms`;
}

const THROUGHPUT_UNITS = ["B/s", "KiB/s", "MiB/s", "GiB/s"];

/** Formats a throughput given in bytes per second, using binary units. */
export function formatThroughput(bytesPerSecond: number): string {
  let value = bytesPerSecond;
  let unit = 0;
  while (value >= 1024 && unit < THROUGHPUT_UNITS.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${significant(value)} ${THROUGHPUT_UNITS[unit]}`;
}
