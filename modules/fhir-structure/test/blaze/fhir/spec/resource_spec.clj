(ns blaze.fhir.spec.resource-spec
  (:require
   [blaze.fhir.parsing-context.spec]
   [blaze.fhir.spec.resource :as res]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef res/create-type-handlers
  :args (s/cat :structure-definitions (s/coll-of map?) :opts map?)
  :ret (s/or :type-handlers :blaze.fhir/parsing-context :anomaly ::anom/anomaly))
