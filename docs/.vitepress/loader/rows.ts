// Reading of the committed benchmark data the performance charts are rendered
// from.
//
// This module runs in Node only. A page's data loader (`*.data.ts`) calls it
// during `vitepress build`, so neither the file contents nor this code reach
// the client bundle — only the parsed rows of the files the page lists, and
// only in that page's chunk.
import { readFileSync } from "node:fs";
import { basename } from "node:path";
import type { Row } from "../theme/chart/data";

/**
 * Parses the data file at the absolute path `file`.
 *
 * The separator follows the file type, just like the `set datafile separator`
 * of the replaced gnuplot scripts: `|` for the `.txt` measurement tables, `,`
 * for the `.csv` k6 results.
 */
function rows(file: string): Row[] {
  const separator = file.endsWith(".csv") ? "," : "|";
  const rows = readFileSync(file, "utf-8")
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 0 && !line.startsWith("#"))
    // The measurement tables pad their columns to a fixed width. Trimming the
    // cells here rather than on every read keeps that padding out of the page
    // chunk the rows are serialized into.
    .map((line) => line.split(separator).map((cell) => cell.trim()));
  // A file that holds nothing but comments passes every check a chart makes:
  // no rows still split into the right number of series, and the axis of an
  // empty series falls back to its `y-min`. The page would then render an
  // empty chart rather than fail, so the emptiness is caught here, at the file
  // that caused it.
  if (rows.length === 0) {
    throw new Error(`no data rows in ${file}`);
  }
  return rows;
}

/**
 * Parses each of the data files `files`, keyed by file name.
 *
 * This is the `load` function of every data loader whose page plots table
 * data. The files come in as the absolute paths VitePress resolved the
 * loader's `watch` list to.
 */
export function loadRows(files: string[]): Record<string, Row[]> {
  return Object.fromEntries(files.map((file) => [basename(file), rows(file)]));
}
