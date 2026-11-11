(ns blaze.operation.graph.compiler-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.fhir.spec.spec]
   [blaze.operation.graph :as-alias graph]
   [blaze.operation.graph.compiler :as c]
   [blaze.operation.graph.compiler.spec]
   [blaze.value-set-expander.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef c/compile
  :args (s/cat :graph-def :fhir/GraphDefinition)
  :ret (s/or :compiled-graph ::graph/compiled-graph :anomaly ::anom/anomaly))

(s/fdef c/node-type-matches?
  :args (s/cat :node-type string? :type string?)
  :ret boolean?)

(s/fdef c/expand-value-sets
  :args (s/cat :value-set-expander :blaze/value-set-expander
               :graph ::graph/compiled-graph)
  :ret ac/completable-future?)
