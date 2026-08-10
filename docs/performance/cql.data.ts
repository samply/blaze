import { defineLoader } from "vitepress";
import { loadRows } from "../.vitepress/loader/rows";
import type { Row } from "../.vitepress/theme/chart/data";

// The files are listed one by one rather than matched by a glob, so that a
// page ships exactly the data it plots. `performance/cql` holds twice as many
// measurement tables as this page shows.
export declare const data: Record<string, Row[]>;

export default defineLoader({
  watch: [
    "./cql/all-code-search-1M.txt",
    "./cql/code-value-search-100k.txt",
    "./cql/code-value-search-1M.txt",
    "./cql/inpatient-stress-search-1M.txt",
    "./cql/medication-search-100k.txt",
    "./cql/medication-search-1M.txt",
    "./cql/medication-ten-search-100k.txt",
    "./cql/medication-ten-search-1M.txt",
    "./cql/simple-code-search-100k-fh.txt",
    "./cql/simple-code-search-100k.txt",
    "./cql/simple-code-search-1M.txt",
    "./cql/ten-code-search-100k-fh.txt",
    "./cql/ten-code-search-100k.txt",
    "./cql/ten-code-search-1M.txt",
  ],
  load: loadRows,
});
