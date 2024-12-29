(ns blaze.terminology-service.local.code-system.ucum
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.async.comp :as ac]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.luid :as luid]
   [blaze.terminology-service.local.code-system.core :as c]
   [blaze.terminology-service.local.code-system.util :as u]
   [cognitect.anomalies :as anom]
   [taoensso.timbre :as log])
  (:import
   [org.fhir.ucum UcumEssenceService UcumService]))

(set! *warn-on-reflection* true)

(def ^:private service
  (let [classloader (.getContextClassLoader (Thread/currentThread))]
    (with-open [stream (.getResourceAsStream classloader "ucum-essence.xml")]
      (UcumEssenceService. stream))))

(defn- validate [code]
  (when (.validate ^UcumService service code)
    (ba/incorrect (format "The provided code `%s` was not found in the code system `http://unitsofmeasure.org`." code))))

(defmethod c/validate-code :ucum
  [{:keys [url version]} request]
  (if-ok [code (u/extract-code request (type/value url))
          _ (validate code)]
    {:fhir/type :fhir/Parameters
     :parameter
     [(u/parameter "result" #fhir/boolean true)
      (u/parameter "code" (type/code code))
      (u/parameter "system" #fhir/uri"http://unitsofmeasure.org")
      (u/parameter "version" version)]}
    (fn [{::anom/keys [message]}]
      {:fhir/type :fhir/Parameters
       :parameter
       [(u/parameter "result" #fhir/boolean false)
        (u/parameter "message" (type/string message))]})))

(defmethod c/expand-concept :ucum
  [_ _ concepts]
  (into
   []
   (keep
    (fn [{:keys [code]}]
      (when (nil? (.validate ^UcumService service (type/value code)))
        {:system #fhir/uri"http://unitsofmeasure.org"
         :code code})))
   concepts))

(defn- ucum-query [db]
  (d/type-query db "CodeSystem" [["url" "http://unitsofmeasure.org"]]))

(defn- luid [{:keys [clock rng-fn]}]
  (luid/luid clock (rng-fn)))

(defn- create-code-system [{:keys [node] :as context}]
  (log/debug "Create UCUM code system...")
  (d/transact
   node
   [[:create
     {:fhir/type :fhir/CodeSystem
      :id (luid context)
      :url #fhir/uri"http://unitsofmeasure.org"
      :version #fhir/string"2013.10.21"
      :name #fhir/string"UCUM"
      :title #fhir/string"Unified Code for Units of Measure (UCUM)"
      :status #fhir/code"active"
      :experimental #fhir/boolean false
      :date #fhir/dateTime"2013-10-21"
      :caseSensitive #fhir/boolean true
      :content #fhir/code"not-present"}]]))

(defn ensure-code-system
  "Ensures that the UCUM code system is present in the database node."
  [{:keys [node] :as context}]
  (if-let [_ (coll/first (ucum-query (d/db node)))]
    (ac/completed-future nil)
    (create-code-system context)))
