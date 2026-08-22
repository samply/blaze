import { defineLoader } from "vitepress";
import { loadRows } from "../.vitepress/loader/rows";
import type { Row } from "../.vitepress/theme/chart/data";

// The files are listed one by one rather than matched by a glob, so that a
// page ships exactly the data it plots. `performance/fhir-search` also holds
// unrelated measurement data: `condition-codes-disease-10k.txt` alone is
// 108 KB.
export declare const data: Record<string, Row[]>;

export default defineLoader({
  watch: [
    "./fhir-search/chart-data/code-date-patient-search-download-1M.txt",
    "./fhir-search/chart-data/code-patient-search-download-1M.txt",
    "./fhir-search/chart-data/code-value-search-download-1M.txt",
    "./fhir-search/chart-data/multiple-codes-patient-search-download-1M.txt",
    "./fhir-search/chart-data/multiple-codes-search-download-1M.txt",
    "./fhir-search/chart-data/multiple-codes-search-vs-download-1M.txt",
    "./fhir-search/chart-data/multiple-search-param-search-download-1M.txt",
    "./fhir-search/chart-data/patient-date-search-download-1M.txt",
    "./fhir-search/chart-data/simple-code-search-download-1M.txt",
    "./fhir-search/chart-data/simple-date-search-download-1M.txt",
    "./fhir-search/chart-data/token-forward-chaining-search-download-1M.txt",
  ],
  load: loadRows,
});
