(ns blaze.elm.compiler.library-spec
  (:require
   [blaze.anomaly-spec]
   [blaze.db.spec]
   [blaze.elm.compiler :as-alias c]
   [blaze.elm.compiler.library :as library]
   [blaze.elm.compiler.library.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as-alias anom]))

(s/fdef library/compile-library
  :args (s/cat :node :blaze.db/node :library :elm/library :opts ::c/options)
  :ret (s/or :library ::c/library :anomaly ::anom/anomaly))

(s/fdef library/resolve-all-refs
  :args (s/cat :expression-defs ::c/expression-defs)
  :ret (s/or :expression-defs ::c/expression-defs :anomaly ::anom/anomaly))

(s/fdef library/resolve-params
  :args (s/cat :expression-defs ::c/expression-defs :parameters ::c/parameters)
  :ret ::c/expression-defs)

(s/fdef library/optimize
  :args (s/cat :node :blaze.db/node :expression-defs ::c/expression-defs)
  :ret ::c/expression-defs)
