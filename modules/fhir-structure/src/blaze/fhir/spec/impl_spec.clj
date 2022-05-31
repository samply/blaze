(ns blaze.fhir.spec.impl-spec
  (:require
    [blaze.fhir.spec.impl :as impl]
    [clojure.spec.alpha :as s]))


(s/fdef impl/primitive-type->spec-defs
  :args (s/cat :struct-def (comp #{"primitive-type"} :kind)))
