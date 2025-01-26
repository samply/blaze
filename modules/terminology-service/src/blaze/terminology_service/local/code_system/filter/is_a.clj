(ns blaze.terminology-service.local.code-system.filter.is-a
  "Includes all concepts that have a transitive is-a relationship with the
  concept provided as the value, including the provided concept itself
  (include descendant codes and self)."
  (:require
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system.filter.core :as core]
   [blaze.terminology-service.local.graph :as graph]))

(defmethod core/filter-concepts :is-a
  [{:default/keys [graph]} {:keys [value]}]
  (graph/is-a graph (type/value value)))

(defmethod core/find-concept :is-a
  [{:default/keys [graph]} {:keys [value]} code]
  (graph/find-is-a graph value code))
