import { defineLoader } from "vitepress";
import { loadRows } from "../../.vitepress/loader/rows";
import type { Row } from "../../.vitepress/theme/chart/data";

export declare const data: Record<string, Row[]>;

export default defineLoader({
  watch: ["./double-code-search-100k.txt"],
  load: loadRows,
});
