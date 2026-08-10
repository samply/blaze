import { defineLoader } from "vitepress";
import { loadRows } from "../.vitepress/loader/rows";
import type { Row } from "../.vitepress/theme/chart/data";

// The files are listed one by one rather than matched by a glob, so that a
// page ships exactly the data it plots.
export declare const data: Record<string, Row[]>;

export default defineLoader({
  watch: [
    "./load-testing/data/transaction-A5N46.csv",
    "./load-testing/data/transaction-LEA47.csv",
    "./load-testing/data/transaction-LEA79.csv",
  ],
  load: loadRows,
});
