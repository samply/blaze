# Transaction Load Test History

## Intent

The transaction load test records the version of Blaze it ran against, but each new run overwrites the result of the previous one. To follow performance improvements over time and to spot regressions, the results of several versions are kept side by side, and the website lets a reader pick which versions a chart shows.

## Goals

* Keep one result per system and release (e.g. `1.10.0`, `1.11.0`), plus optionally one newer run of a development build, named by the commit hash the build reports as its version.
* Keep all results in the current commit. Results are never recovered from the git history, because when the testing method changes, all stored releases are re-run with the new method. So every stored result is always measured the same way, and no result carries a method marker.
* Make it a single `make` call to run the test against any release or a development build.
* On the website, every system's chart gets its own row of version buttons listing only the versions measured on that system. The latest version is selected by default. Up to two versions can be selected at a time.

## Non-Goals

* No recovery of earlier results from the git history.
* No automatic (CI) runs. The test stays a manual run on the system under test.
* No history for the other load tests (patient everything, patient graph).

## Approaches

1. **One file per system and version (chosen).** `data/transaction/<SYSTEM>/<version>.csv` in today's format. The chart reads all files of its system's directory and derives the version buttons from the file names. Re-running a version overwrites just that file.
2. **One file per system with appended rows**, the existing `version` column telling the runs apart. Fewer files, but re-running a version means cutting its rows out of the middle of the file, and the `make` target can no longer name the version.
3. **Option 1 plus a manifest** listing the versions per system with labels or dates. Gives control over order and labels, but is one more thing to keep in step with the directory, and the file names already carry everything the chart needs.

Option 1 was chosen.

## Design

### Data Layout

* Published medians: `docs/performance/load-testing/data/transaction/<SYSTEM>/<version>.csv`.
* Single runs: `docs/performance/load-testing/data/raw/transaction/<SYSTEM>/<version>-<run>.csv`.
* The format of both stays as it is: `vus,requests_per_s,med_ms,p95_ms,p99_ms,version`.
* A version is a release if it matches `<major>.<minor>.<patch>`. Every other version is the commit hash of a development build.
* Per system there is at most one commit run. Two commit hashes can't be ordered, so the docs build fails if a system's directory holds more than one.
* The existing results move to `data/transaction/<SYSTEM>/5773c58b.csv` and their raw runs to `data/raw/transaction/<SYSTEM>/5773c58b-<run>.csv`.

### Running the Test

A single phony target `transaction` runs the test, so it always runs, even if the result file of that version already exists. It takes the system in `SYSTEM` and, for a release, the version in `VERSION`:

* **Release:** `make transaction SYSTEM=<SYSTEM> VERSION=<version>` (e.g. `make transaction SYSTEM=LEA47 VERSION=1.10.0`) runs the test three times against the image `samply/blaze:<version>` on that system and writes the median to `data/transaction/<SYSTEM>/<version>.csv`. The `Makefile` passes the expected version to k6, and `setup()` fails if the version the server reports differs from it, so a result can't land in the file of another version.
* **Development build:** `make transaction SYSTEM=<SYSTEM>` without `VERSION` runs the test three times against the image `blaze:latest`. That image is built on the system under test, often from an uberjar copied onto it, so the checkout there may not be on the commit of the build, and the commit hash isn't known when calling `make`. The version Blaze reports in `/metadata` is the commit hash, so the target takes it from there and writes the median to `data/transaction/<SYSTEM>/<hash>.csv`, with the raw runs named after it as well. The new commit run replaces the previous commit run of that system, median and raw runs, so there is never more than one.
* Without `SYSTEM`, the target fails with a message naming the known systems.
* The Compose files take the image from an environment variable instead of pinning `samply/blaze:main`, so both targets use the same Compose file per system.
* `median.sh` keeps its check that all runs carry the same version.
* The `all` target no longer builds the transaction results, because there is no fixed set of versions to build.

### Chart

* Every system's chart gets its own row of version buttons above it, listing the versions of that system's directory. Releases come first, in ascending version order, followed by the commit run.
* The newest version is selected by default, so the statically rendered page shows it.
* At most two versions can be selected. Selecting a third drops the one selected first. The last selected version can't be deselected.
* With two versions selected, both are drawn in the same chart on the same axes. The colours keep meaning the metric (requests/s, median, P95, P99), the older version is drawn dashed and the newer one solid, and the legend explains the two line styles by version. The title names the selected version(s).
* The data loader bundles the new directories, so their files are available to the chart.

### Page Text

* The text of `docs/performance/load-testing.md` keeps describing the newest run, the one selected by default. The sentence naming the commit says so and points out that up to two versions can be compared.
* The methodology section describes the new `make` call, the data layout, the commit run rule and that all stored releases are re-run when the method changes.

## Left to the Builder

* Whether the versioned chart is a new component wrapping the plotting of `LineChart` or an extension of `LineChart`. The dashed line style already exists on `Curve`.
* The name of the image environment variable of the Compose files.
* How `make transaction` learns the commit hash of a development build, e.g. by asking the started server for its version before the first run.
* How the version buttons look, within the existing theme.
* Which older releases to backfill is decided when running the test. It's not part of this change.

## Verification

* `npm run docs:build` succeeds with the migrated data, and fails with two commit runs in one system's directory.
* In the browser, every chart shows the latest version by default and switches, compares and drops versions as described. A temporary second version file is enough to check this.
