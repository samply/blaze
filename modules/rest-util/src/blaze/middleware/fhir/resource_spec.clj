(ns blaze.middleware.fhir.resource-spec
  (:require
   [blaze.middleware.fhir.resource :as resource]
   [clojure.spec.alpha :as s]))

(s/fdef resource/wrap-resource
  :args (s/cat :handler ifn?))

(s/fdef resource/wrap-binary-data
  :args (s/cat :handler ifn?))
