(ns blaze.elm.compiler.library-spec
  (:require
   [blaze.anomaly-spec]
   [blaze.db.spec]
   [blaze.elm.compiler :as-alias c]
   [blaze.elm.compiler.library :as library]
   [blaze.elm.compiler.library.spec]
   [blaze.elm.expression :as expr]
   [blaze.elm.expression.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as-alias anom]))

(s/fdef library/compile-library
  :args (s/cat :context ::c/context :library :elm/library :opts ::c/options)
  :ret (s/or :library ::c/library :anomaly ::anom/anomaly))

(s/fdef library/resolve-all-refs
  :args (s/cat :expression-defs ::c/expression-defs)
  :ret (s/or :expression-defs ::c/expression-defs :anomaly ::anom/anomaly))

(s/fdef library/resolve-params
  :args (s/cat :expression-defs ::c/expression-defs :parameters ::c/parameters)
  :ret ::c/expression-defs)

(s/fdef library/eval-unfiltered
  :args (s/cat :context ::expr/context :expression-defs ::c/expression-defs)
  :ret ::c/expression-defs)

(s/fdef library/optimize
  :args (s/cat :db :blaze.db/db :expression-defs ::c/expression-defs)
  :ret ::c/expression-defs)
