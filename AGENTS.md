# Agent Instructions

## Project Goal

The goal of this project is to provide a FHIR® Server with an internal CQL Evaluation Engine which is able to answer population wide aggregate queries in a timely manner to enable interactive, online queries over millions of patients.

## Project Structure

* **Language:** Main language is Clojure with some parts written in Java.
* **Build System:** Clojure `tools.deps`. All build steps are available via `Makefiles`.
* **Modules:** Most code is located in `modules/`. Each module is structured similarly.
  * **Frontend:** A special module `modules/frontend` written in Svelte.
* **Server:** The FHIR Server is built via the top-level `Makefile`.

## Code Conventions & Idioms

Rigorous adherence to these patterns is required:

* **Functional Core:** Blaze is primarily implemented using **pure functions**.
* **Error Handling:** Use **[Cognitect Anomalies](https://github.com/cognitect-labs/anomalies/)** for error handling. Do **not** use Exceptions for control flow.
* **Component System:**
  * Use **[Integrant](https://github.com/weavejester/integrant)** for wiring components.
  * Implement `ig/init-key` and `ig/halt-key!` multimethods.
  * Define `m/pre-init-spec` for dependency validation.
* **Specs:**
  * Every public function must have a spec.
  * **Location:** Specs must never be defined inline in the implementation namespace. There are two distinct spec namespace conventions:
    * `s/def` (data/attribute specs) → dot-separated `*.spec` namespace (e.g., `blaze.db.node.spec` for `blaze.db.node`), in a `spec.clj` file nested under the namespace directory.
    * `s/fdef` (function specs) → hyphen-separated `*-spec` namespace (e.g., `blaze.db.node-spec` for `blaze.db.node`), as a sibling file to the implementation.
  * **Classpath:** Public module-level specs go in `src`, but inner-module public function specs should be in `test` to keep the production classpath small.
* **Java Interop:**
  * Avoid reflection.
  * **Mandatory:** Add `(set! *warn-on-reflection* true)` to any namespace performing Java interop.
* **Async Composition:**
  * Prefer the `blaze.async.comp/do-sync` macro over threading chains of `blaze.async.comp/then-apply` where feasible.
  * Always use `blaze.async.comp/then-apply`, `blaze.async.comp/then-compose` and similar in conjunction with the threading macro (`->`), never as a standalone call.
* **Macros:**
  * Macros must always be `:refer`ed directly so they can be used without a namespace alias prefix (e.g., `[blaze.async.comp :refer [do-sync]]`).
* **Reuse:**
  * Avoid code duplication.
  * Use existing functions if possible.
  * Create a function if code is used more than two times.
* **Testing:**
  * **Test-Driven Development (mandatory):** Never change production code without first writing a failing test that captures the new behaviour. Write the test, see it fail, then make it pass. This applies to bug fixes (regression test first), new features, and contract changes (e.g. allowing an anomaly return value).
  * **Spec Instrumentation:** Always enable spec instrumentation in tests to catch spec violations early.
    * Require `[clojure.spec.test.alpha :as st]`.
    * Call `(st/instrument)` at the top level of the test namespace.
  * **Java Interop:** Enable reflection warnings (`(set! *warn-on-reflection* true)`) ONLY if the test namespace performs Java interop.
  * **Fixtures:** Use the standard test fixture in all test namespaces.
    * Require `[blaze.test-util :as tu]`.
    * Call `(test/use-fixtures :each tu/fixture)`.
  * **Assertions:** Use the `given` macro from `juxt.iota` for asserting map values (e.g. anomalies).
    * Require `[juxt.iota :refer [given]]`.
    * Example: `(given (my-fn ...) ::anom/category := ::anom/fault ::anom/message := "...")`
    * Use `is` only for simple scalar equality checks that don't involve maps.
  * **Async Testing:**
    * To assert that a `CompletableFuture` completes *exceptionally* with an anomaly, use `given-failed-future` from `blaze.module.test-util` — **not** the `(given (ba/try-anomaly (ac/join ...)))` pattern.
    * To obtain the value of a successfully-completed future inside a test, use `@future` (Clojure's `deref`).
  * **Private Functions:** Do **not** call private functions (via `#'`) from tests. If a function needs to be tested, move it to an `impl` namespace where it becomes part of the public API of that namespace.

## Documentation

* **Environment Variables:** Every new environment variable introduced via `#blaze/cfg` in `resources/blaze.edn` must be documented in `docs/deployment/environment-variables.md`, following the existing format (heading, description, default value, since badge).

## Verification & Workflow

When starting to work on an issue, you can use the GitHub CLI to fetch the issue details: `gh issue view <issue-number>`

When **creating** an issue, classify it via GitHub's native issue **type** (e.g. `Bug`, `Feature`), not via a `bug`/`feature` label. The `gh issue create` flag for this is `--type` (e.g. `gh issue create --type Bug ...`). Only labels that aren't covered by a type (e.g. `module:db`) should be passed via `--label`.

Before finishing a task, ensure the following commands pass:

1.  **Format:** `make fmt`
2.  **Lint:** `make lint` (Uses `clj-kondo`)
3.  **Test:** Run tests only for the modules you changed: `make -C modules/<module> test` (e.g. `make -C modules/db test`). Use `make test` only when changes span multiple modules or the root.
4.  **Coverage:** `make test-coverage` (Checks for adequate test coverage — must be **≥ 95% forms**)

When adding a **new module** under `modules/`, also add it to the `module` matrix in `.github/workflows/build.yml` (the `test` job, sorted alphabetically) so CI picks it up.

After verification, when working on an issue:

1. Create a feature branch using the GitHub CLI: `gh issue develop <issue-number> --checkout`
2. Commit the changes: `git add .` and `git commit`
   * The commit title should be the issue title.
   * The commit body should just contain: `Closes: #<issue-number>`
3. There should be exactly one commit per issue. Multiple changes have to be ammended to the first commit.
