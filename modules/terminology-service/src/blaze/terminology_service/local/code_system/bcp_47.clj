(ns blaze.terminology-service.local.code-system.bcp-47
  "https://www.rfc-editor.org/bcp/bcp47.txt
  https://datatracker.ietf.org/doc/html/rfc5646
  https://www.iana.org/assignments/language-subtags-tags-extensions/language-subtags-tags-extensions.xhtml#language-subtags"
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.module :as m]
   [blaze.terminology-service.local.code-system.core :as c]
   [blaze.terminology-service.local.code-system.util :as cs-u]
   [blaze.util :refer [str]]
   [clojure.string :as str]
   [taoensso.timbre :as log])
  (:import
   [java.util Locale]))

(set! *warn-on-reflection* true)

(def ^:private code-system
  {:fhir/type :fhir/CodeSystem
   :meta
   #fhir/Meta
    {:tag
     [#fhir/Coding
       {:system #fhir/uri-interned "https://samply.github.io/blaze/fhir/CodeSystem/AccessControl"
        :code #fhir/code "read-only"}]}
   :url #fhir/uri-interned "urn:ietf:bcp:47"
   :version #fhir/string "1.0.0"
   :name #fhir/string "BCP-47"
   :title #fhir/string "BCP-47 Tags for Identifying Languages"
   :status #fhir/code "active"
   :experimental #fhir/boolean false
   :content #fhir/code "not-present"})

(defn- blank-to-nil [s]
  (when-not (str/blank? s)
    s))

(defn- locale-details [^Locale locale]
  (let [language (.getDisplayLanguage locale)
        country (blank-to-nil (.getDisplayCountry locale))
        script (blank-to-nil (.getDisplayScript locale))
        details (keep identity [(some->> script (str "Script="))
                                (some->> country (str "Region="))])]
    [(.toLanguageTag locale)
     (cond-> language
       (seq details)
       (str " (" (str/join ", " details) ")"))]))

(def ^:private locales
  (into {} (map locale-details) (Locale/getAvailableLocales)))

(defmethod c/find :bcp-47
  [& _]
  (ac/completed-future code-system))

(defmethod c/enhance :bcp-47
  [_ code-system]
  code-system)

(defmethod c/expand-complete :bcp-47
  [_ _]
  (ba/conflict "Expanding all BCP-47 concepts is not possible."))

(defn- display [code]
  (locales (str/trim code)))

(defmethod c/expand-concept :bcp-47
  [_ concepts _]
  (into
   []
   (keep
    (fn [{:keys [code]}]
      (when-let [display (display (:value code))]
        {:system #fhir/uri-interned "urn:ietf:bcp:47"
         :code code
         :display display})))
   concepts))

(defmethod c/find-complete :bcp-47
  [{:keys [url version]} {{:keys [code]} :clause}]
  (when-let [display (display code)]
    {:code (type/code code) :display display :system url :version version}))

(defn- bcp-47-query [db]
  (d/type-query db "CodeSystem" [["url" "urn:ietf:bcp:47"]]))

(defn- create-code-system [{:keys [node] :as context}]
  (log/debug "Create BCP-47 code system...")
  (d/transact node [(cs-u/tx-op code-system (m/luid context))]))

(defn ensure-code-system
  "Ensures that the BCP-47 code system is present in the database node."
  [{:keys [node] :as context}]
  (if-let [_ (coll/first (bcp-47-query (d/db node)))]
    (ac/completed-future nil)
    (create-code-system context)))
