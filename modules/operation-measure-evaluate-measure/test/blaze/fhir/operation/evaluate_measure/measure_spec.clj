(ns blaze.fhir.operation.evaluate-measure.measure-spec
  (:require
    [blaze.cql-translator-spec]
    [blaze.db.spec]
    [blaze.fhir.operation.evaluate-measure.cql-spec]
    [blaze.fhir.operation.evaluate-measure.measure :as measure]
    [blaze.fhir.operation.evaluate-measure.measure.spec]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [reitit.core :as reitit])
  (:import
    [java.time OffsetDateTime]
    [java.time.temporal Temporal]))


(defn- temporal? [x]
  (instance? Temporal x))


(s/def ::period
  (s/tuple temporal? temporal?))


(s/def ::params
  (s/keys
    :req-un
    [::period
     :blaze.fhir.operation.evaluate-measure/report-type]))


(s/fdef measure/evaluate-measure
  :args
  (s/cat
    :now #(instance? OffsetDateTime %)
    :db :blaze.db/db
    :router reitit/router?
    :measure :blaze/resource
    :params ::params)
  :ret
  (s/or :result (s/keys :req-un [:blaze/resource] :opt-un [:blaze.db/tx-ops])
        :anomaly ::anom/anomaly))
