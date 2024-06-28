(ns blaze.elm.expression.cache-spec
  (:require
   [blaze.db.tx-log.spec]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.expression :as-alias expr]
   [blaze.elm.expression.cache :as ec]
   [blaze.elm.expression.cache.bloom-filter :as-alias bloom-filter]
   [blaze.elm.expression.cache.bloom-filter-spec]
   [blaze.elm.expression.cache.bloom-filter.spec]
   [blaze.elm.expression.cache.spec]
   [blaze.fhir.spec.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef ec/get
  :args (s/cat :cache ::expr/cache :expression core/expr?)
  :ret (s/nilable ::ec/bloom-filter))

(s/fdef ec/get-disk
  :args (s/cat :cache ::expr/cache :hash ::bloom-filter/hash)
  :ret (s/or :result ::ec/bloom-filter :anomaly ::anom/anomaly))

(s/fdef ec/delete-disk!
  :args (s/cat :cache ::expr/cache :hash ::bloom-filter/hash)
  :ret (s/nilable ::anom/anomaly))

(s/fdef ec/list-by-t
  :args (s/cat :cache ::expr/cache)
  :ret (s/nilable ::ec/bloom-filter))

(s/fdef ec/total
  :args (s/cat :cache ::expr/cache)
  :ret nat-int?)
