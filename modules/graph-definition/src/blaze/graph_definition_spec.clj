(ns blaze.graph-definition-spec
  (:require
   [blaze.fhir.spec.spec]
   [blaze.graph-definition :as gd]
   [blaze.graph-definition.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef gd/compile
  :args (s/cat :graph-def :fhir/GraphDefinition)
  :ret (s/or :compiled-graph ::gd/compiled-graph :anomaly ::anom/anomaly))
