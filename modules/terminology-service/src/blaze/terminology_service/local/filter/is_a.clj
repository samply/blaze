(ns blaze.terminology-service.local.filter.is-a
  "Includes all concepts that have a transitive is-a relationship with the
  concept provided as the value, including the provided concept itself
  (include descendant codes and self)."
  (:require
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.filter.core :as core]
   [blaze.terminology-service.local.graph :as graph]))

(defmethod core/filter-concepts :is-a
  [{:keys [value]} concepts]
  (graph/is-a (graph/build-graph concepts) (type/value value)))
