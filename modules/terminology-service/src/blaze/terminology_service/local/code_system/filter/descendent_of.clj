(ns blaze.terminology-service.local.code-system.filter.descendent-of
  "Includes all concepts that have a transitive is-a relationship with the
  concept provided as the value (include descendant codes)."
  (:require
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system.filter.core :as core]
   [blaze.terminology-service.local.graph :as graph]))

(defmethod core/filter-concepts :descendent-of
  [{:keys [value]} {:default/keys [graph]}]
  (graph/descendent-of graph (type/value value)))
