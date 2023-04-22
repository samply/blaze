(ns blaze.interaction.search.util
  (:require
    [blaze.handler.fhir.util :as fhir-util]))


(def ^:const match
  #fhir/BundleEntrySearch{:mode #fhir/code"match"})


(def ^:const include
  #fhir/BundleEntrySearch{:mode #fhir/code"include"})


(defn entry
  ([context resource]
   (entry context resource match))
  ([context {:fhir/keys [type] :keys [id] :as resource} mode]
   ;; TODO: revert after discovery why the resource type can be nil
   (cond->
     {:fhir/type :fhir.Bundle/entry
      :resource resource
      :search mode}
     type
     (assoc :fullUrl (fhir-util/instance-url context (name type) id)))))
