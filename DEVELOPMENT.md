# Development

## Pure Functions

For most parts Blaze is implemented using pure functions. Pure function depend only on there arguments and only produce an output without doing any side effects. Pure functions have one important property, they are referentially transparent, meaning it doesn't matter when or how often they are called.

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
    [java.io Closeable]))


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
  (.close ^Closeable node))
```

In this example, you can see the `new-node` function which gets two dependencies `dep-a` and `dep-b` which could be config values or other components. The function returns the database node itself. In our case the database node holds resources which should be freed when it is no longer needed. A common idiom is to implement `java.io.Closeable` and call the `.close` method at the end of usage.

While the pair of the function `new-node` and the method `.close` can be used in tests, integrant is used in production. In the example, you can see the multi-method instances `ig/pre-init-spec`, `ig/init-key` and `ig/halt-key!`. First `ig/pre-init-spec` is used to provide a spec for the dependency map `ig/init-key` receives. The spec is created using the `s/keys` form in order to validate a map. Second the `ig/init-key` method will be called by integrant when the component with the :blaze.db/node key is initialized. In this method we simply call our `new-node` function, passing all dependencies from the map as arguments. In addition to that we log a meaningful message at info level in order to make the startup of Blaze transparent. It's also a good idea to log out any config values here. Last the method `ig/halt-key!` is used to free any resources our component might hold. Here we call our `.close` on the component instance passed.

## Function Specs

Every public function should have a spec. Function specs are declared in a namespace with the suffix `_spec` appended to the namespace of the function. In case the function is public on module level, the spec namespace resides in the `src` folder, otherwise it resides in the `test` folder. Having specs of inner-module public functions in the test folder ensures that they can be used in tests but also removes them from the overall class path of Blaze. Not having such specs on the global class path reduces uberjar and memory footprint. In addition to that it also reduces the number of instrumented functions in inter-module tests.

## Java Interop

It is important that we don't use reflection. In order to see reflection warnings ```(set! *warn-on-reflection* true)``` should be used in every namespace which does Java interop.

[1]: <https://github.com/weavejester/integrant>
