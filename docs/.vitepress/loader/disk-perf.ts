// Parsing of the disk performance measurement results the disk-perf charts are
// rendered from.
//
// The committed result files are the `Parameters` resources the `$disk-perf`
// operation returned, taken as they came out of the server, so the numbers on
// the page can't drift away from the measurement.
//
// This module runs in Node only. Parsing at build time is what keeps the 10 KB
// of `Parameters` JSON per system out of the client bundle: the page ships the
// roughly twenty numbers of `DiskPerf` instead.
import { readFileSync } from "node:fs";
import { basename } from "node:path";
import type { DiskPerf, ReadRun } from "../theme/chart/disk-perf";

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

/** Parses the measurement result at the absolute path `file`. */
function diskPerf(file: string): DiskPerf {
  const resource = JSON.parse(readFileSync(file, "utf-8")) as Parameters;
  if (resource.resourceType !== "Parameters") {
    throw new Error(
      `${file} is a ${resource.resourceType} resource, not a Parameters resource`,
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

/**
 * Parses each of the measurement results `files`, keyed by file name.
 *
 * This is the `load` function of the data loader of `performance/disk-perf.md`.
 * The files come in as the absolute paths VitePress resolved the loader's
 * `watch` list to.
 */
export function loadDiskPerf(files: string[]): Record<string, DiskPerf> {
  return Object.fromEntries(
    files.map((file) => [basename(file), diskPerf(file)]),
  );
}
