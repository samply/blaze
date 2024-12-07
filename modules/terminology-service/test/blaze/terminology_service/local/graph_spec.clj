(ns blaze.terminology-service.local.graph-spec
  (:require
   [blaze.fhir.spec.spec]
   [blaze.terminology-service.local.graph :as graph]
   [clojure.spec.alpha :as s]))

(s/fdef graph/build-graph
  :args (s/cat :concepts (s/coll-of :fhir.CodeSystem/concept))
  :ret (s/coll-of :fhir.CodeSystem/concept))
