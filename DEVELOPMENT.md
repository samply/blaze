# Development Guide for Blaze

## Development Environment

### OS

It is best to use a Unix-like (*Nix) OS for Blaze development because the whole workflow depends on tools that are not as easily installed on Windows (like `GNU Make` etc.). If, for some reason, you have to use Windows, it is recommended that you install WSL i.e. [Windows Subsystem for Linux](https://en.wikipedia.org/wiki/Windows_Subsystem_for_Linux). Please check the article [How to install Linux on Windows with WSL](https://learn.microsoft.com/en-us/windows/wsl/install), if you are new to WSL.

#### WSL

The default Linux distribution (on WSL) is Ubuntu (though it can be changed). If you are familiar with Ubuntu commands on how to install additional packages using `sudo apt-get` or `sudo apt` commands, then the same commands work in WSL too.

### VCS (Git)

#### Line Endings

Since Windows and Linux / macOS handle line endings differently, it's better to follow a consistent line ending approach. Windows uses a combination of a carriage return and a line feed (CR+LF) characters to represent a new line, whereas, Linux / macOS use a single line feed (LF) character to represent a new line. Please check the article [How to verify the line ending configuration in a Git repository](https://labex.io/questions/how-to-verify-the-line-ending-configuration-in-a-git-repository-12650) to configure the correct line endings. Otherwise, everytime you try to commit a file, you'll see unnecessary differences in line endings. 

**Note**: In IntelliJ Idea, you'll notice either LF (which is **correct**) or CRLF (for Windows, which is _incorrect_) in the status bar (which shows the Line Separator for the current file).

#### Globally ignoring certain files

It is best to globally ignore certain files from Git e.g. IDE specific files or directories. Hence, please create a `~/.gitignore` file with the following contents -

```gitignore
.calva
.idea
.vscode

*.iml
```

Now, we need to register this global `gitignore` file with git, using -

```shell
git config --global core.excludesfile ~/.gitignore
```

### Java/JVM

As per Clojure's [Java compatibility](https://clojure.org/releases/downloads#_java_compatibility) guide, Clojure is compiled to Java 8 compatible bytecode, so installing Java 8 is also fine. However, the [current stable release version](https://clojure.org/releases/downloads#stable) (which is 1.12.0, as of this writing) supports LTS Java versions 8, 11, 17 & 21, so theoretically, you could install any of these versions of the open-source JDKs, such as [OpenJDK](https://openjdk.org/) or [Eclipse Temurin](https://adoptium.net/).

Since we use certain Java 17 features directly, we need at least Java 17. We also test with Java versions 17 and 21 in CI. So those are the only two Java versions a developer should use, i.e. **Java 17 or 21**.

### IDE

Although any Clojure-aware IDE (Emacs, [IntelliJ Idea](https://www.jetbrains.com/idea/) with [Cursive](https://cursive-ide.com/) plugin, [VS Code](https://code.visualstudio.com/) with [Calva](https://calva.io/) plugin, Vim, VSCodium...) is fine, _please make sure_ that **you know how to run module level REPLs** using your favorite IDE. If you are not sure of this, then it's best to default to using IntelliJ Idea. You may refer [Installing IntelliJ IDEA on Ubuntu](https://medium.com/@ahsanilyasabbasi/installing-intellij-idea-on-ubuntu-71237d41bc12) if you have any doubts.

### Other Required tools

The latest LTS/stable releases of:
* [nodejs](https://nodejs.org/en)
* GNU Make
* [Clojure, with CLI tools](https://clojure.org/guides/install_clojure)
* clj-kondo
* cljfmt: `clj -Ttools install io.github.weavejester/cljfmt '{:git/tag "`[`<latest-stable-release>`](https://github.com/weavejester/cljfmt/releases/latest)`"}' :as cljfmt`
* [actionlint](https://github.com/rhysd/actionlint)

## Building Blaze

Blaze is built as a single Docker image, along with a separate frontend image.
There is also an uberjar for standalone use.

### Using GitHub CI

The most reliable way to build Blaze is through GitHub CI. When you create a pull request (PR), a Docker image with the label `pr-<num>` is built. You can use this image after the pipeline completes successfully.

### Using a Local Build Environment

Blaze is written in Clojure, a modern LISP for the JVM.

#### Steps

1. Install SUSHI:

   ```sh
   make build-job-ig
   ```

2. Create the uberjar in the `target` directory:

   ```sh
   make uberjar
   ```

3. Build the Blaze Docker image:

   ```sh
   docker build -t blaze:latest .
   ```

4. Build the frontend Docker image:

   ```sh
   make build-frontend
   ```

## Developing Blaze

### REPL-driven Development in Blaze

As for writing code in any LISP in general, the recommended way to hack on Blaze is to use REPL-Driven Development (RDD). This is, to fire up a REPL, connect to it, and evaluate the running system within your IDE as you change it. [More information about RDD](https://clojure.org/guides/repl/introduction).

Since Blaze is organized into modules, you can fire up a REPL in either of two ways: from the root directory ("a global REPL") or from the specific module you are currently working on ("a local REPL"). A global REPL is better suited for local end-to-end (E2E) testing, running and exploration - it loads the entire system. In contrast, a local REPL is better suited for focused work on a particular module - it only loads the bare minimum amount of namespaces required to make that module function in isolation. Moreover, local REPLs provide you with a faster feedback loop, since they enable you to eval the module's (unit) *tests* - something you simply cannot do from a global REPL, since they are not included in its classpath.

#### Running a System REPL

You can run a REPL to run Blaze as a system using the following Makefile alias:

```make emacs-repl```

For more details, see the `Makefile`, `deps.edn`, and `dev/blaze/dev.clj` files.

#### Running a Remote REPL Into Container

1. add `-Dclojure.server.repl='{:address,\"0.0.0.0\",:port,5555,:accept,clojure.core.server/repl}'` to the `JAVA_TOOL_OPTIONS` env var
2. bind port 5555
3. create the remote REPL in your IDE, and connect to it.


### Tests and their Importance

Developing a new feature will always include writing the corresponding unit and/or integration tests. Whether you write them upfront or after the fact is up to you. That being said, writing them before/while you actually implement a new feature may make it easier to reason about and assess the feature in the works. Whatever the case, the tests will make it easier to ensure that the new feature is implemented correctly, both at module and system level.

### Blaze's CI Pipeline

This project uses a CI pipeline, which checks:
* unit tests,
* integration tests, and
* code coverage (which should only increase on each commit).
For more details, see the  `.github/` directory.

### Configuration

The configuration of the development system is done with the same environment variables used in the production system.
Documentation: [Environment Variables](docs/deployment/environment-variables.md).

## Release Checklist

1. Create a release branch named `release-v<version>`, e.g., `release-v0.29.0`.
2. Update all occurrences of the old version (e.g., `0.28.0`) to the new version (e.g., `0.29.0`).
3. Update `unreleased` badges in the documentation.
4. Update the `CHANGELOG.md` based on the milestone.
5. Create a commit with the title `Release v<version>`.
6. Create a PR from the release branch to `main`.
7. Merge the PR.
8. Create and push a tag named `v<version>`, e.g., `v0.13.1`, on `main` at the merge commit.
9. Copy the release notes from the `CHANGELOG.md` into the GitHub release.

## Code Conventions

### Style Guide

Follow the [Clojure Style Guide][2], enforced by `cljfmt`. For more details, check the `cljfmt.edn` file.

### Pure Functions

Blaze is primarily implemented using pure functions. Pure functions depend only on their arguments and produce an output without side effects. This makes them referentially transparent, meaning their behavior does not change based on when or how often they are called.

### Error Handling

Blaze uses [anomalies][3] for error handling, instead of exceptions. Anomalies separate the error context from the error itself without interrupting the execution flow. For more information, see the [anomaly module](modules/anomaly/README.md).

### Components

Components are entities within Blaze and may have state.
An example of a stateful component is the local database node.
Components reside in a namespace with a constructor function called `new-<component-name>`.
In production, we use the [integrant][1] library to wire all of Blaze components together.

#### Example:

```clojure
(ns blaze.db.node
  (:require
    [blaze.module :as m]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig])
  (:import
    [java.lang AutoCloseable]))


(defn new-node
  "Creates a new local database node."
  [dep-a dep-b])


(defmethod m/pre-init-spec :blaze.db/node [_]
  (s/keys :req-un [dep-a dep-b]))


(defmethod ig/init-key :blaze.db/node
  [_ {:keys [dep-a dep-b]}]
  (log/info "Open local database node")
  (new-node dep-a dep-b))


(defmethod ig/halt-key! :blaze.db/node
  [_ node]
  (log/info "Close local database node")
  (.close ^AutoCloseable node))
```

* `new-node` creates a new local database node with dependencies `dep-a` and `dep-b`.
* `halt-key!`` implements `AutoCloseable` to ensure resources are properly released when the node is closed.
* `m/pre-init-spec` provides a spec for the dependency map to ensure correct configuration.
* `ig/init-key` initializes the node and logs a meaningful message at info level.
* `ig/halt-key!` closes the node and releases any held resources.

### Function Specs

Every public function should have a spec. Function specs are declared in a namespace with the suffix `-spec` appended to the function's namespace. Public module-level function specs reside in the `src` folder, while inner-module public function specs reside in the `test` folder. This ensures that specs are used in tests but not included in the global classpath, reducing the uberjar and memory footprint.

### Java Interop

Avoid using reflection. To enable reflection warnings, add `(set! *warn-on-reflection* true)` to each namespace with Java interop.

[1]: <https://github.com/weavejester/integrant>
[2]: <https://github.com/bbatsov/clojure-style-guide>
[3]: <https://github.com/cognitect-labs/anomalies/>
