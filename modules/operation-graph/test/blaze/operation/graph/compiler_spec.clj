(ns blaze.operation.graph.compiler-spec
  (:require
   [blaze.fhir.spec.spec]
   [blaze.operation.graph :as-alias graph]
   [blaze.operation.graph.compiler :as c]
   [blaze.operation.graph.compiler.spec]
   [clojure.spec.alpha :as s]))

(s/fdef c/compile
  :args (s/cat :graph-def :fhir/GraphDefinition)
  :ret ::graph/compiled-graph)
