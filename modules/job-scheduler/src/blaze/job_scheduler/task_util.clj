(ns blaze.job-scheduler.task-util
  (:require
   [blaze.fhir.spec.type :as type]))

(defn code-value [system {:keys [coding]}]
  (some #(when (= system (type/value (:system %))) (type/value (:code %))) coding))

(defn input-value
  {:arglists '([system code task])}
  [system code {:keys [input]}]
  (some #(when (= code (code-value system (:type %))) (type/value (:value %))) input))
