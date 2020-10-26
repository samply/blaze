(ns blaze.interaction.search.util
  (:require
    [blaze.fhir.spec.type :as type]
    [blaze.handler.fhir.util :as fhir-util]))


(defn entry
  [router {:fhir/keys [type] :keys [id] :as resource}]
  {:fullUrl (type/->Uri (fhir-util/instance-url router (name type) id))
   :resource resource
   :search {:fhir/type :fhir.Bundle.entry/search :mode #fhir/code"match"}})
