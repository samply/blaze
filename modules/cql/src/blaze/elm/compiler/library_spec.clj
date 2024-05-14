(ns blaze.elm.compiler.library-spec
  (:require
   [blaze.anomaly-spec]
   [blaze.elm.compiler :as-alias c]
   [blaze.elm.compiler-spec]
   [blaze.elm.compiler.library :as library]
   [blaze.elm.compiler.library.spec]
   [blaze.elm.compiler.spec]
   [blaze.fhir.spec.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef library/compile-library
  :args (s/cat :node :blaze.db/node :library :elm/library :opts map?)
  :ret (s/or :library ::c/library :anomaly ::anom/anomaly))
