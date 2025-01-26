(ns blaze.terminology-service.local.code-system.ucum
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.luid :as luid]
   [blaze.terminology-service.local.code-system.core :as c]
   [taoensso.timbre :as log])
  (:import
   [org.fhir.ucum UcumEssenceService UcumService]))

(set! *warn-on-reflection* true)

(def ^:private service
  (let [classloader (.getContextClassLoader (Thread/currentThread))]
    (with-open [stream (.getResourceAsStream classloader "ucum-essence.xml")]
      (UcumEssenceService. stream))))

(def ^:private code-system
  {:fhir/type :fhir/CodeSystem
   :url #fhir/uri"http://unitsofmeasure.org"
   :version #fhir/string"2013.10.21"
   :name #fhir/string"UCUM"
   :title #fhir/string"Unified Code for Units of Measure (UCUM)"
   :status #fhir/code"active"
   :experimental #fhir/boolean false
   :date #fhir/dateTime"2013-10-21"
   :caseSensitive #fhir/boolean true
   :content #fhir/code"not-present"})

(defmethod c/find :ucum
  [& _]
  (ac/completed-future code-system))

(defmethod c/enhance :ucum
  [_ code-system]
  code-system)

(defmethod c/expand-complete :ucum
  [_ _]
  (ba/conflict "Expanding all UCUM concepts is not possible."))

(defn- valid? [code]
  (nil? (.validate ^UcumService service code)))

(defmethod c/expand-concept :ucum
  [_ concepts _]
  (into
   []
   (keep
    (fn [{:keys [code]}]
      (when (valid? (type/value code))
        {:system #fhir/uri"http://unitsofmeasure.org"
         :code code})))
   concepts))

(defmethod c/find-complete :ucum
  [{:keys [url version]} {{:keys [code]} :clause}]
  (when (valid? code)
    {:code (type/code code) :system url :version version}))

(defn- ucum-query [db]
  (d/type-query db "CodeSystem" [["url" "http://unitsofmeasure.org"]]))

(defn- luid [{:keys [clock rng-fn]}]
  (luid/luid clock (rng-fn)))

(defn- create-code-system [{:keys [node] :as context}]
  (log/debug "Create UCUM code system...")
  (d/transact node [[:create (assoc code-system :id (luid context))]]))

(defn ensure-code-system
  "Ensures that the UCUM code system is present in the database node."
  [{:keys [node] :as context}]
  (if-let [_ (coll/first (ucum-query (d/db node)))]
    (ac/completed-future nil)
    (create-code-system context)))
