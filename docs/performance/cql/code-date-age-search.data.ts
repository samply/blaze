import { defineLoader } from "vitepress";
import { loadRows } from "../../.vitepress/loader/rows";
import type { Row } from "../../.vitepress/theme/chart/data";

export declare const data: Record<string, Row[]>;

export default defineLoader({
  watch: ["./code-date-age-search-100k.txt"],
  load: loadRows,
});
