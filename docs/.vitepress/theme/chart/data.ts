// The row model the performance charts are rendered from.
//
// A page hands a chart its already parsed rows, read at build time by the
// page's data loader (`.vitepress/loader/rows.ts`). The chart therefore builds
// its SVG synchronously in `setup()` without any browser API, so `vitepress
// build` renders it into the static HTML instead of painting it after
// hydration.
//
// Only the helpers a chart needs to pick values out of a row live here. They
// are free of any Node API, because this module is part of the client bundle.

/**
 * Returns `data`, failing with the name of the chart if there is none.
 *
 * A page picks the data of a chart by a key into the record its data loader
 * returns. A key the loader doesn't hold yields `undefined`, which would
 * otherwise fail deep inside the chart rather than at the chart that names it.
 */
export function required<T>(data: T | undefined, chart: string): T {
  if (data === undefined) {
    throw new Error(`the chart "${chart}" was given no data`);
  }
  return data;
}

/**
 * A row of a data file, split into its trimmed cells.
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
  return value;
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
