(ns blaze.fhir.operation.evaluate-measure.measure-spec
  (:require
    [blaze.db.api-spec]
    [blaze.fhir.operation.evaluate-measure.measure :as measure]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]
    [reitit.core :as reitit])
  (:import
    [java.time OffsetDateTime]
    [java.time.temporal Temporal]))


(defn- temporal? [x]
  (instance? Temporal x))


(s/fdef measure/evaluate-measure
  :args
  (s/cat
    :now #(instance? OffsetDateTime %)
    :node :blaze.db/node
    :db :blaze.db/db
    :router reitit/router?
    :period (s/tuple temporal? temporal?)
    :measure :blaze/resource))
