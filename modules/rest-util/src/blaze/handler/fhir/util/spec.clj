(ns blaze.handler.fhir.util.spec
  (:require
   [blaze.rest-api :as-alias rest-api]
   [blaze.spec]
   [clojure.spec.alpha :as s]))

(s/def ::rest-api/batch-handler
  fn?)

(s/def :blaze.handler.fhir.util/interaction
  string?)

(s/def :blaze.handler.fhir.util/type
  :fhir.resource/type)

(s/def :blaze.handler.fhir.util/id
  :blaze.resource/id)
