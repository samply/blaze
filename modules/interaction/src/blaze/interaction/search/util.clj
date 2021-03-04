(ns blaze.interaction.search.util
  (:require
    [blaze.fhir.spec.type :as type]
    [blaze.handler.fhir.util :as fhir-util]))


(def ^:private match
  (type/map->BundleEntrySearch {:mode #fhir/code"match"}))


(defn entry [router {:fhir/keys [type] :keys [id] :as resource}]
  {:fhir/type :fhir.Bundle/entry
   :fullUrl (type/->Uri (fhir-util/instance-url router (name type) id))
   :resource resource
   :search match})
