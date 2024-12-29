(ns blaze.terminology-service.local.graph-spec
  (:require
   [blaze.fhir.spec.spec]
   [blaze.terminology-service.local :as-alias local]
   [blaze.terminology-service.local.graph :as graph]
   [blaze.terminology-service.local.graph.spec]
   [clojure.spec.alpha :as s]))

(s/fdef graph/build-graph
  :args (s/cat :concepts (s/coll-of :fhir.CodeSystem/concept))
  :ret ::local/graph)

(s/fdef graph/is-a
  :args (s/cat :graph ::local/graph :code string?)
  :ret (s/coll-of :fhir.CodeSystem/concept))

(s/fdef graph/descendent-of
  :args (s/cat :graph ::local/graph :code string?)
  :ret (s/coll-of :fhir.CodeSystem/concept))
