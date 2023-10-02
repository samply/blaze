(ns blaze.elm.expression.cache-spec
  (:require
   [blaze.db.tx-log.spec]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.expression :as-alias expr]
   [blaze.elm.expression.cache :as ec]
   [blaze.elm.expression.cache.bloom-filter-spec]
   [blaze.elm.expression.cache.spec]
   [blaze.fhir.spec.spec]
   [clojure.spec.alpha :as s]))

(s/fdef ec/get
  :args (s/cat :cache ::expr/cache :expression core/expr?)
  :ret (s/nilable ::ec/bloom-filter))
