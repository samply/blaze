(ns blaze.interaction.search.util
  (:require
   [blaze.handler.fhir.util :as fhir-util]))

(def ^:const match
  #fhir/BundleEntrySearch{:mode #fhir/code"match"})

(def ^:const include
  #fhir/BundleEntrySearch{:mode #fhir/code"include"})

(def ^:const outcome
  #fhir/BundleEntrySearch{:mode #fhir/code"outcome"})

(defn full-url [context {:fhir/keys [type] :keys [id]}]
  (fhir-util/instance-url context (name type) id))

(defn match-entry [context resource]
  {:fhir/type :fhir.Bundle/entry
   :fullUrl (full-url context resource)
   :resource resource
   :search match})

(defn include-entry [context resource]
  {:fhir/type :fhir.Bundle/entry
   :fullUrl (full-url context resource)
   :resource resource
   :search include})

(defn outcome-entry [_ resource]
  {:fhir/type :fhir.Bundle/entry
   :resource resource
   :search outcome})
