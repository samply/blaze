import { defineLoader } from "vitepress";
import { loadRows } from "../.vitepress/loader/rows";
import type { Row } from "../.vitepress/theme/chart/data";

// The files are listed one by one rather than matched by a glob, so that a
// page ships exactly the data it plots.
export declare const data: Record<string, Row[]>;

export default defineLoader({
  watch: [
    "./terminology-service/data/value-set-validate-code-alpha-id.csv",
    "./terminology-service/data/value-set-validate-code-laboratory-observation.csv",
    "./terminology-service/data/value-set-validate-code-snomed-ct-body-site.csv",
  ],
  load: loadRows,
});
