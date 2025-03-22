(ns blaze.middleware.fhir.resource-spec
  (:require
   [blaze.fhir.parsing-context.spec]
   [blaze.fhir.spec.spec]
   [blaze.middleware.fhir.resource :as resource]
   [clojure.spec.alpha :as s]))

(s/fdef resource/wrap-resource
  :args (s/cat :handler ifn? :parsing-context :blaze.fhir/parsing-context
               :type :fhir.resource/type))

(s/fdef resource/wrap-binary-data
  :args (s/cat :handler ifn? :parsing-context :blaze.fhir/parsing-context))
