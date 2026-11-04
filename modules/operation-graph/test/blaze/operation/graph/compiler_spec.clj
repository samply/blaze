(ns blaze.operation.graph.compiler-spec
  (:require
   [blaze.fhir.spec.spec]
   [blaze.operation.graph :as-alias graph]
   [blaze.operation.graph.compiler :as c]
   [blaze.operation.graph.compiler.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef c/compile
  :args (s/cat :graph-def :fhir/GraphDefinition)
  :ret (s/or :compiled-graph ::graph/compiled-graph :anomaly ::anom/anomaly))
