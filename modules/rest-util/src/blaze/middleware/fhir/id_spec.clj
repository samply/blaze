(ns blaze.middleware.fhir.id-spec
  (:require
   [blaze.middleware.fhir.id :as id]
   [clojure.spec.alpha :as s]))

(s/fdef id/wrap-invalid-id
  :args (s/cat :handler fn?)
  :ret fn?)
