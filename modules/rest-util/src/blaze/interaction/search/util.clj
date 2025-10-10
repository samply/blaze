(ns blaze.interaction.search.util
  (:require
   [blaze.db.search-param :as-alias sp]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.module :as m]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]))

(def ^:const match
  #fhir.Bundle.entry/search{:mode #fhir/code "match"})

(def ^:const include
  #fhir.Bundle.entry/search{:mode #fhir/code "include"})

(def ^:const outcome
  #fhir.Bundle.entry/search{:mode #fhir/code "outcome"})

(defn- full-url [context {:fhir/keys [type] :keys [id]}]
  (type/uri (fhir-util/instance-url context (name type) id)))

(defn match-entry [context resource]
  (let [match-extension (-> resource meta ::sp/match-extension)]
    {:fhir/type :fhir.Bundle/entry
     :fullUrl (full-url context resource)
     :resource resource
     :search (cond-> match
               match-extension
               (assoc :extension match-extension))}))

(defn include-entry [context resource]
  {:fhir/type :fhir.Bundle/entry
   :fullUrl (full-url context resource)
   :resource resource
   :search include})

(defn outcome-entry [_ resource]
  {:fhir/type :fhir.Bundle/entry
   :resource resource
   :search outcome})

(defmethod m/pre-init-spec ::link [_]
  (s/keys :req [:fhir/version]))

;; A component that depends on the FHIR version and once initialized, is a
;; function that creates bundle links.
;;
;; The function takes a `relation` and a `url` as strings.
(defmethod ig/init-key ::link
  [_ {:fhir/keys [version]}]
  (condp = version
    "4.0.1"
    (fn link [relation url]
      {:fhir/type :fhir.Bundle/link
       :relation (type/string-interned relation)
       :url (type/uri url)})
    (fn link [relation url]
      {:fhir/type :fhir.Bundle/link
       :relation (type/code relation)
       :url (type/uri url)})))
