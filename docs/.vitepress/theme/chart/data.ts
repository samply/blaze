// Loading and parsing of the committed benchmark data the performance charts
// are rendered from.
//
// The data files are pulled in eagerly as raw strings so that a chart component
// can build its SVG synchronously in `setup()`. That keeps the charts free of
// any browser API, so they are rendered into the static HTML by `vitepress
// build` instead of being painted after hydration.
//
// The globs are deliberately narrow: `docs/performance` also holds unrelated
// measurement data (`fhir-search/condition-codes-disease-10k.txt` alone is
// 108 KB) that must not end up in the bundle.
const RAW = import.meta.glob<string>(
  [
    "../../../performance/cql/*.txt",
    "../../../performance/disk-perf/*.json",
    "../../../performance/fhir-search/chart-data/*.txt",
    "../../../performance/load-testing/data/*.csv",
    "../../../performance/terminology-service/data/*.csv",
  ],
  { query: "?raw", import: "default", eager: true },
);

// The glob keys are relative to this file. Re-key them by their path below
// `performance/`, so `src` is written the same way from every page, no matter
// how deep that page sits.
const FILES: Record<string, string> = Object.fromEntries(
  Object.entries(RAW).map(([path, content]) => [
    path.slice(path.indexOf("/performance/") + "/performance/".length),
    content,
  ]),
);

/**
 * A row of a data file, split into its cells.
 *
 * Cells are addressed by gnuplot's 1-based column numbers, so a chart's
 * `y-col`/`x-col` is literally the number the replaced gnuplot script used in
 * its `using` clause. For the pipe-separated files that means column 1 is the
 * empty cell in front of the leading `|`, exactly as gnuplot saw it.
 */
export type Row = string[];

/** Returns the 1-based column `col` of `row`. */
export function cell(row: Row, col: number): string {
  const value = row[col - 1];
  if (value === undefined) {
    throw new Error(`missing column ${col} in row: ${row.join("|")}`);
  }
  return value.trim();
}

const NUMBER = /^[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?/;

/**
 * Returns the leading number of the 1-based column `col` of `row`.
 *
 * Only the leading number is read, which reproduces gnuplot's tolerance of the
 * ` k` suffix the measurement tables use.
 */
export function num(row: Row, col: number): number {
  const value = cell(row, col);
  const match = NUMBER.exec(value);
  if (match === null) {
    throw new Error(`column ${col} is not a number: ${value}`);
  }
  return parseFloat(match[0]);
}

/** Returns the content of the data file `src`, given relative to `docs/performance`. */
export function content(src: string): string {
  const content = FILES[src];
  if (content === undefined) {
    throw new Error(
      `unknown chart data file: ${src}\nknown files:\n  ${Object.keys(FILES).sort().join("\n  ")}`,
    );
  }
  return content;
}

/**
 * Parses the data file `src`, given relative to `docs/performance`.
 *
 * The separator follows the file type, just like the `set datafile separator`
 * of the replaced gnuplot scripts: `|` for the `.txt` measurement tables, `,`
 * for the `.csv` k6 results.
 */
export function rows(src: string): Row[] {
  const separator = src.endsWith(".csv") ? "," : "|";
  return content(src)
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 0 && !line.startsWith("#"))
    .map((line) => line.split(separator));
}

/**
 * Splits `all` into `count` series, taking every `count`-th row.
 *
 * This is gnuplot's `every count::index`, plus the two assertions gnuplot never
 * made: the row count has to be a multiple of the series count.
 */
export function series(all: Row[], count: number): Row[][] {
  if (count < 1) {
    throw new Error(`series count must be positive but was ${count}`);
  }
  if (all.length % count !== 0) {
    throw new Error(
      `${all.length} rows can't be split into ${count} series of equal size`,
    );
  }
  return Array.from({ length: count }, (_, i) =>
    all.filter((_, j) => j % count === i),
  );
}

/**
 * Returns the category of each row of `rows`, asserting that every series
 * yields the same categories.
 */
export function categories(rows: Row[][], col: number): string[] {
  const first = rows[0].map((row) => cell(row, col));
  for (const other of rows.slice(1)) {
    const categories = other.map((row) => cell(row, col));
    if (JSON.stringify(categories) !== JSON.stringify(first)) {
      throw new Error(
        `series categories differ: [${first}] vs [${categories}]`,
      );
    }
  }
  return first;
}
