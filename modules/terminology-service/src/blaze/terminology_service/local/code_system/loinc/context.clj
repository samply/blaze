(ns blaze.terminology-service.local.code-system.loinc.context
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.spec.type :as type]
   [clojure.data.csv :as csv]
   [clojure.java.io :as io])
  (:import
   [java.util.zip GZIPInputStream]))

(set! *warn-on-reflection* true)

(def ^:const ^String url "http://loinc.org")
(def ^:const ^String version "2.78")
(def ^:const ^String core-table "blaze/terminology_service/local/code_system/loinc/LoincTableCore.csv.gz")

(defn- code-system []
  {:fhir/type :fhir/CodeSystem
   :url (type/uri url)
   :version (type/string version)
   :name #fhir/string"LOINC"
   :title #fhir/string"LOINC Code System"
   :status #fhir/code"active"
   :experimental #fhir/boolean false
   :publisher #fhir/string"Regenstrief Institute, Inc."
   :description #fhir/markdown"LOINC is a freely available international standard for tests, measurements, and observations"
   :copyright #fhir/markdown"This material contains content from LOINC (http://loinc.org). LOINC is copyright Regenstrief Institute, Inc. and the Logical Observation Identifiers Names and Codes (LOINC) Committee and is available at no cost under the license at http://loinc.org/license. LOINC® is a registered United States trademark of Regenstrief Institute, Inc."
   :caseSensitive #fhir/boolean false
   :valueSet #fhir/canonical"http://loinc.org/vs"
   :hierarchyMeaning #fhir/code"is-a"
   :compositional #fhir/boolean false
   :versionNeeded #fhir/boolean false
   :content #fhir/code"not-present"
   :property
   [{:fhir/type :fhir.CodeSystem/property
     :code #fhir/code"CLASS"
     :uri #fhir/uri"http://loinc.org/property/CLASS"
     :description #fhir/string"An arbitrary classification of terms for grouping related observations together"
     :type #fhir/code"Coding"}
    {:fhir/type :fhir.CodeSystem/property
     :code #fhir/code"STATUS"
     :uri #fhir/uri"http://loinc.org/property/STATUS"
     :description #fhir/string"Status of the term. Within LOINC, codes with STATUS=DEPRECATED are considered inactive. Current values: ACTIVE, TRIAL, DISCOURAGED, and DEPRECATED"
     :type #fhir/code"string"}
    {:fhir/type :fhir.CodeSystem/property
     :code #fhir/code"CLASSTYPE"
     :uri #fhir/uri"http://loinc.org/property/CLASSTYPE"
     :description #fhir/string"1=Laboratory class; 2=Clinical class; 3=Claims attachments; 4=Surveys"
     :type #fhir/code"string"}]})

(defn- concept [code long-common-name status]
  (cond->
   {:system #fhir/uri"http://loinc.org"
    :code (type/code code)
    :display (type/string long-common-name)}
    (= "DEPRECATED" status) (assoc :inactive #fhir/boolean true)))

(defn parse-class-type [class-type]
  (case (parse-long class-type)
    1 :laboratory-class
    2 :clinical-class
    3 :claims-attachments
    4 :surveys
    (ba/incorrect (format "Invalid class type `%s`." class-type))))

(defn- read-core-table []
  (let [classloader (.getContextClassLoader (Thread/currentThread))]
    (when-let [in (.getResourceAsStream classloader core-table)]
      (with-open [reader (io/reader (GZIPInputStream. in))]
        (reduce
         (fn [index [code _component _property _TIME_ASPCT _SYSTEM _SCALE_TYP
                     _METHOD_TYP class class-type long-common-name _SHORTNAME
                     _EXTERNAL_COPYRIGHT_NOTICE status]]
           (let [concept (concept code long-common-name status)]
             (-> (assoc-in index [:concept-index code] concept)
                 (update-in [:class-index (keyword class)] (fnil conj []) concept)
                 (update-in [:status-index (keyword status)] (fnil conj []) concept)
                 (update-in [:class-type-index (parse-class-type class-type)] (fnil conj []) concept))))
         {}
         (rest (csv/read-csv reader)))))))

(defn build []
  (assoc (read-core-table) :code-systems [(code-system)]))
