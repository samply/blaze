// Parsing and formatting of the disk performance measurement results the
// disk-perf charts are rendered from.
//
// The committed result files are the `Parameters` resources the `$disk-perf`
// operation returned, taken as they came out of the server, so the numbers on
// the page can't drift away from the measurement.
import { content } from "./data";

/** A single `parameter` (or `part`) entry of a `Parameters` resource. */
interface Parameter {
  name: string;
  valueQuantity?: { value?: number };
  valueDecimal?: number;
  valueBoolean?: boolean;
  valueCode?: string;
  valuePositiveInt?: number;
  part?: Parameter[];
}

interface Parameters {
  resourceType?: string;
  parameter?: Parameter[];
}

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

function parameter(parameters: Parameter[], name: string): Parameter {
  const parameter = parameters.find((parameter) => parameter.name === name);
  if (parameter === undefined) {
    throw new Error(`missing parameter: ${name}`);
  }
  return parameter;
}

function value<T>(
  parameters: Parameter[],
  name: string,
  of: (parameter: Parameter) => T | undefined,
): T {
  const value = of(parameter(parameters, name));
  if (value === undefined) {
    throw new Error(`missing value of parameter: ${name}`);
  }
  return value;
}

const quantity = (parameters: Parameter[], name: string) =>
  value(parameters, name, (parameter) => parameter.valueQuantity?.value);

function readRun(parts: Parameter[]): ReadRun {
  return {
    concurrency: value(parts, "concurrency", (part) => part.valuePositiveInt),
    iops: quantity(parts, "iops"),
    throughput: quantity(parts, "throughput"),
    latencyP50: quantity(parts, "latency-p50"),
    latencyP95: quantity(parts, "latency-p95"),
    latencyP99: quantity(parts, "latency-p99"),
    latencyMax: quantity(parts, "latency-max"),
  };
}

/**
 * Parses the measurement result `src`, given relative to `docs/performance`.
 */
export function diskPerf(src: string): DiskPerf {
  const resource = JSON.parse(content(src)) as Parameters;
  if (resource.resourceType !== "Parameters") {
    throw new Error(
      `${src} is a ${resource.resourceType} resource, not a Parameters resource`,
    );
  }
  const parameters = resource.parameter ?? [];
  // The sweep is the one output that isn't a single named parameter, so it's
  // also the one a missing-value check doesn't cover. Without this guard an
  // empty sweep reaches the charts, where it degenerates into an axis spanning
  // [Infinity, -Infinity] and a reduce over no runs — failing far away from the
  // file that caused it.
  const readRuns = parameters
    .filter((parameter) => parameter.name === "rand-read")
    .map((parameter) => readRun(parameter.part ?? []))
    .sort((a, b) => a.concurrency - b.concurrency);
  if (readRuns.length === 0) {
    throw new Error("missing parameter: rand-read");
  }
  return {
    seqWriteThroughput: quantity(parameters, "seq-write-throughput"),
    readRuns,
    fsyncRate: quantity(parameters, "fsync-rate"),
    fsyncLatencyP50: quantity(parameters, "fsync-latency-p50"),
    fsyncLatencyP95: quantity(parameters, "fsync-latency-p95"),
    fsyncLatencyP99: quantity(parameters, "fsync-latency-p99"),
    directIo: value(
      parameters,
      "direct-io",
      (parameter) => parameter.valueBoolean,
    ),
    score: value(parameters, "score", (parameter) => parameter.valueDecimal),
    rating: value(parameters, "rating", (parameter) => parameter.valueCode),
  };
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
