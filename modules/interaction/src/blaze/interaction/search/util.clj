(ns blaze.interaction.search.util
  (:require
    [blaze.fhir.spec.type :as type]
    [blaze.handler.fhir.util :as fhir-util]))


(def ^:const match
  #fhir/BundleEntrySearch{:mode #fhir/code"match"})


(def ^:const include
  #fhir/BundleEntrySearch{:mode #fhir/code"include"})


(defn entry
  ([context resource]
   (entry context resource match))
  ([context {:fhir/keys [type] :keys [id] :as resource} mode]
   {:fhir/type :fhir.Bundle/entry
    :fullUrl (type/uri (fhir-util/instance-url context (name type) id))
    :resource resource
    :search mode}))
