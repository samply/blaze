(ns blaze.fhir.operation.evaluate-measure.measure-spec
  (:require
   [blaze.cql-translator-spec]
   [blaze.db.spec]
   [blaze.fhir.operation.evaluate-measure.cql-spec]
   [blaze.fhir.operation.evaluate-measure.measure :as measure]
   [blaze.fhir.operation.evaluate-measure.measure.spec]
   [blaze.fhir.spec]
   [blaze.http.spec]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]
   [reitit.core :as reitit])
  (:import
   [java.time.temporal Temporal]))

(defn- temporal? [x]
  (instance? Temporal x))

(s/def ::period
  (s/tuple temporal? temporal?))

(s/def ::params
  (s/keys
   :req-un
   [::period
    ::measure/report-type]
   :opt-un
   [::measure/subject-ref]))

(s/fdef measure/evaluate-measure
  :args
  (s/cat
   :context (s/keys :req [:blaze/base-url ::reitit/router]
                    :req-un [:blaze/clock :blaze/rng-fn :blaze.db/db])
   :measure :blaze/resource
   :params ::params)
  :ret
  (s/or :result (s/keys :req-un [:blaze/resource] :opt-un [:blaze.db/tx-ops])
        :anomaly ::anom/anomaly))
