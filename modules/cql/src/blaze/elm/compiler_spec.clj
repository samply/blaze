(ns blaze.elm.compiler-spec
  (:require
   [blaze.db.spec]
   [blaze.elm.compiler :as c]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.library-spec]
   [blaze.elm.compiler.spec]
   [blaze.elm.expression :as-alias expr]
   [blaze.elm.expression.cache :as-alias ec]
   [blaze.elm.expression.cache.bloom-filter.spec]
   [blaze.elm.expression.spec]
   [blaze.elm.spec]
   [blaze.fhir.spec-spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef c/compile
  :args (s/cat :context :elm/compile-context :expression :elm/expression)
  :ret core/expr?)

(s/fdef c/attach-cache
  :args (s/cat :expression core/expr? :cache ::expr/cache)
  :ret (s/tuple core/expr? (s/coll-of (s/or :bloom-filter ::ec/bloom-filter :anomaly ::anom/anomaly))))

(s/fdef c/resolve-refs
  :args (s/cat :expression core/expr? :expression-defs ::c/expression-defs)
  :ret core/expr?)

(s/fdef c/resolve-params
  :args (s/cat :expression core/expr? :parameters ::c/parameters)
  :ret core/expr?)

(s/fdef c/optimize
  :args (s/cat :expression core/expr? :db :blaze.db/db)
  :ret core/expr?)

(s/fdef c/form
  :args (s/cat :expression core/expr?)
  :ret list?)
