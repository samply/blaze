# Development

## Building Blaze

The main build artefact of Blaze is a single Docker image. Apart from the Docker image, an uberjar is build, which can be used also.

### Using GitHub CI

The most reliable way to build Blaze is to use GitHub CI. If you create a PR, a Docker image with the label `pr-<num>` is created. You can use that image after the pipeline ended successfully.   

### Using a Local Build Environment

* install at least Java 11. Java 17 is preferred
* install Clojure by following this [guide](https://clojure.org/guides/install_clojure)
* install Make
* run `make uberjar` to create the uberjar that will be available under the `target` directory
* run `docker build .` to build the Docker image

## Developing Blaze

The recommended way to write new code for Blaze is to open a REPL in the module you like to work on. Blaze uses Clojures own build system [Deps](https://clojure.org/guides/deps_and_cli). You can run a REPL in the command line by starting the tool `clj` inside the module directory.

The best way to use a REPL, is to use it from your IDE. If you use Intellij, there is a Plugin called [Cursive](https://cursive-ide.com). With Cursive you can create REPL's using the Deps build system. In such a REPL you can also execute the unit tests.

Inside the REPL you should be able to discover and play with the functions and execute unit tests. Developing a new feature will always include writing unit tests. Code coverage is measured in CI and should only increase. The unit tests should already ensure that the feature is implemented correctly on a module level. In addition to that, integration tests can be added to the GitHub CI pipeline available in the file `.github/workflows/build.yml`.

In addition to the REPL development inside a single module, it's possible to run a REPL were Blaze can be started as a system. Such a REPL should be started with the namespace `blaze.dev` loaded available in the file `dev/blaze/dev.clj`. In that namespace Blaze can be started by invoking the `init` function. The configuration of the development system is done with the same environment variables used in the production system. That variables are documented [here](docs/deployment/environment-variables.md).

## Release Checklist

* create a release branch called `release-v<version>` like `release-v0.13.1`
* rename every occurrence of the old version, say `0.13.0` into the new version, say `0.13.1`
* rename every occurrence of old Docker images like `samply/blaze:0.13` into the new image, say `samply/blaze:0.14`
* update the last changed date in `blaze.rest-api.capabilities`
* update the CHANGELOG based on the milestone
* create a commit with the title `Release v<version>`
* create a PR from the release branch into master
* merge that PR
* create and push a tag called `v<version>` like `v0.13.1` on master at the merge commit
* merge the release branch back into develop
* create release notes on GitHub

## Style Guide

The Clojure code in this project follows the [Clojure Style Guide][2].

### Exceptions:

* we use two empty lines between top-level forms

## Pure Functions

For most parts Blaze is implemented using pure functions. Pure function depend only on their arguments and only produce an output without doing any side effects. Pure functions have one important property, they are referentially transparent, meaning it doesn't matter when or how often they are called.

## Components

In a world of functions, there is still a need for some nouns. Such nouns are components, thing that exist in Blaze and possibly have state. A good example is the local database node which represents the local portion of the database, which for sure has state.

Such components reside in a namespace. There exists a constructor function called `new-<component-name>` which takes the dependencies of the component and returns a new instance of it. These constructor functions are mainly used in tests. In production, the library [integrant][1] is used to wire all components together.

### Example:

```clojure
(ns blaze.db.node
  (:require 
    [clojure.spec.alpha :as s]
    [integrant.core :as ig])
  (:import
    [java.lang AutoCloseable]))


(defn new-node
  "Creates a new local database node."
  [dep-a dep-b])


(defmethod ig/pre-init-spec :blaze.db/node [_]
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

In this example, you can see the `new-node` function which gets two dependencies `dep-a` and `dep-b` which could be config values or other components. The function returns the database node itself. In our case the database node holds resources which should be freed when it is no longer needed. A common idiom is to implement `java.lang.AutoCloseable` and call the `.close` method at the end of usage.

While the pair of the function `new-node` and the method `.close` can be used in tests, integrant is used in production. In the example, you can see the multi-method instances `ig/pre-init-spec`, `ig/init-key` and `ig/halt-key!`. First `ig/pre-init-spec` is used to provide a spec for the dependency map `ig/init-key` receives. The spec is created using the `s/keys` form in order to validate a map. Second the `ig/init-key` method will be called by integrant when the component with the :blaze.db/node key is initialized. In this method we simply call our `new-node` function, passing all dependencies from the map as arguments. In addition to that we log a meaningful message at info level in order to make the startup of Blaze transparent. It's also a good idea to log out any config values here. Last the method `ig/halt-key!` is used to free any resources our component might hold. Here we call our `.close` on the component instance passed.

## Function Specs

Every public function should have a spec. Function specs are declared in a namespace with the suffix `_spec` appended to the namespace of the function. In case the function is public on module level, the spec namespace resides in the `src` folder, otherwise it resides in the `test` folder. Having specs of inner-module public functions in the test folder ensures that they can be used in tests but also removes them from the overall class path of Blaze. Not having such specs on the global class path reduces uberjar and memory footprint. In addition to that it also reduces the number of instrumented functions in inter-module tests.

## Java Interop

It is important that we don't use reflection. In order to see reflection warnings ```(set! *warn-on-reflection* true)``` should be used in every namespace which does Java interop.

## REPL

### Remote REPL Into Container

* add `-Dclojure.server.repl='{:address,\"0.0.0.0\",:port,5555,:accept,clojure.core.server/repl}'` to the `JAVA_TOOL_OPTIONS` env var
* bind port 5555
* create remote REPL in Cursive

## Print Debugging

You can use [hashp][3] for print styple debugging.

[1]: <https://github.com/weavejester/integrant>
[2]: <https://github.com/bbatsov/clojure-style-guide>
[3]: <https://github.com/weavejester/hashp>
