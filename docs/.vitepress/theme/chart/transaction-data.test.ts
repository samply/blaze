import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, it } from "node:test";
import { assertVersion, sortVersions } from "./versions.ts";

// The docs build only logs errors thrown while rendering a component, so the
// consistency of the transaction result files is checked here, where a failure
// fails the docs workflow.
const DIR = join(
  import.meta.dirname,
  "../../../performance/load-testing/data/transaction",
);

/** Returns the values of column 6 (version) of the data rows of `path`. */
function versionColumn(path: string): string[] {
  return readFileSync(path, "utf8")
    .split("\n")
    .slice(1)
    .filter((line) => line.trim().length > 0)
    .map((line) => line.split(",")[5].trim());
}

describe("transaction data", () => {
  for (const system of readdirSync(DIR)) {
    it(system, () => {
      const files = readdirSync(join(DIR, system)).filter((file) =>
        file.endsWith(".csv"),
      );
      const versions = files.map((file) => file.slice(0, -".csv".length));

      sortVersions(versions);
      for (const version of versions) {
        assertVersion(
          `${system}/${version}.csv`,
          version,
          versionColumn(join(DIR, system, `${version}.csv`)),
        );
      }
    });
  }
});
