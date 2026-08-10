import { defineLoader } from "vitepress";
import { loadRows } from "./.vitepress/loader/rows";
import type { Row } from "./.vitepress/theme/chart/data";

// The files are listed one by one rather than matched by a glob, so that a
// page ships exactly the data it plots. `performance/cql` holds twice as many
// measurement tables as this page shows.
export declare const data: Record<string, Row[]>;

export default defineLoader({
  watch: ["./performance/cql/simple-code-search-100k.txt"],
  load: loadRows,
});
