(ns blaze.middleware.fhir.output-spec
  (:require
   [blaze.fhir.spec.spec]
   [blaze.fhir.writing-context.spec]
   [blaze.middleware.fhir.output :as fhir-output]
   [clojure.spec.alpha :as s]))

(s/fdef fhir-output/wrap-output
  :args (s/cat :handler ifn? :writing-context :blaze.fhir/writing-context
               :opts (s/? map?)))

(s/fdef fhir-output/wrap-binary-output
  :args (s/cat :handler ifn? :writing-context :blaze.fhir/writing-context))
