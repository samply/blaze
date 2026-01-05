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
  * **Location:** Function specs must reside in a separate namespace with the suffix `-spec` (e.g., `blaze.db.node-spec` for `blaze.db.node`).
  * **Classpath:** Public module-level specs go in `src`, but inner-module public function specs should be in `test` to keep the production classpath small.
* **Java Interop:**
  * Avoid reflection.
  * **Mandatory:** Add `(set! *warn-on-reflection* true)` to any namespace performing Java interop.
* **Reuse:**
  * Avoid code duplication.
  * Use existing functions if possible.
  * Create a function if code is used more than two times.

## Verification & Workflow

Before finishing a task, ensure the following commands pass:

1.  **Format:** `make fmt`
2.  **Lint:** `make lint` (Uses `clj-kondo`)
3.  **Test:** `make test` (Runs module and root tests)

## General Rules

* **Do not commit changes:** Never commit changes to the git repository. The user will handle committing.
