(ns blaze.operation.graph.cache-spec
  (:require
   [blaze.fhir.spec.spec]
   [blaze.operation.graph :as-alias graph]
   [blaze.operation.graph.cache :as cache]
   [blaze.operation.graph.compiler.spec]
   [blaze.operation.graph.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef cache/compile
  :args (s/cat :cache ::graph/compiled-graph-cache :graph-def :fhir/GraphDefinition)
  :ret (s/or :compiled-graph ::graph/compiled-graph :anomaly ::anom/anomaly))
