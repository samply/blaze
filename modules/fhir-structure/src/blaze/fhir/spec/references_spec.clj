(ns blaze.fhir.spec.references-spec
  (:require
   [blaze.fhir.spec.references :as fsr]
   [blaze.fhir.spec.spec]
   [blaze.fhir.spec.type.system-spec]
   [clojure.spec.alpha :as s]))

(s/fdef fsr/split-literal-ref
  :args (s/cat :s string?)
  :ret (s/nilable :blaze.fhir/literal-ref-tuple))
