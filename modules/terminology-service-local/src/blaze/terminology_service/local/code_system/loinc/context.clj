(ns blaze.terminology-service.local.code-system.loinc.context
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.fhir.spec.type :as type]
   [blaze.util :refer [str]]
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(set! *warn-on-reflection* true)

(def ^:const ^String url "http://loinc.org")
(def ^:const ^String value-set-prefix "http://loinc.org/vs/")
(def ^:const ^String version "2.78")
(def ^:const copyright #fhir/markdown "This material contains content from LOINC (http://loinc.org). LOINC is copyright Regenstrief Institute, Inc. and the Logical Observation Identifiers Names and Codes (LOINC) Committee and is available at no cost under the license at http://loinc.org/license. LOINC® is a registered United States trademark of Regenstrief Institute, Inc.")
(def ^:const ^String resources "blaze/terminology_service/local/code_system/loinc/")
(def ^:const ^String table (str resources "LoincTable/Loinc.csv"))
(def ^:const ^String answer-lists (str resources "AccessoryFiles/AnswerFile/AnswerList.csv"))
(def ^:const ^String parts (str resources "AccessoryFiles/PartFile/Part.csv"))

(defn- code-system []
  {:fhir/type :fhir/CodeSystem
   :meta
   #fhir/Meta
    {:tag
     [#fhir/Coding
       {:system #fhir/uri-interned "https://samply.github.io/blaze/fhir/CodeSystem/AccessControl"
        :code #fhir/code "read-only"}]}
   :url (type/uri-interned url)
   :version (type/string version)
   :name #fhir/string "LOINC"
   :title #fhir/string "LOINC Code System"
   :status #fhir/code "active"
   :experimental #fhir/boolean false
   :publisher #fhir/string "Regenstrief Institute, Inc."
   :description #fhir/markdown "LOINC is a freely available international standard for tests, measurements, and observations"
   :copyright copyright
   :caseSensitive #fhir/boolean false
   :valueSet #fhir/canonical "http://loinc.org/vs"
   :hierarchyMeaning #fhir/code "is-a"
   :compositional #fhir/boolean false
   :versionNeeded #fhir/boolean false
   :content #fhir/code "not-present"
   :property
   [{:fhir/type :fhir.CodeSystem/property
     :code #fhir/code "COMPONENT"
     :uri #fhir/uri-interned "http://loinc.org/property/COMPONENT"
     :description #fhir/string "First major axis-component or analyte: Analyte Name, Analyte sub-class, Challenge"
     :type #fhir/code "Coding"}
    {:fhir/type :fhir.CodeSystem/property
     :code #fhir/code "PROPERTY"
     :uri #fhir/uri-interned "http://loinc.org/property/PROPERTY"
     :description #fhir/string "Second major axis-property observed: Kind of Property (also called kind of quantity)"
     :type #fhir/code "Coding"}
    {:fhir/type :fhir.CodeSystem/property
     :code #fhir/code "TIME_ASPCT"
     :uri #fhir/uri-interned "http://loinc.org/property/TIME_ASPCT"
     :description #fhir/string "Third major axis-timing of the measurement: Time Aspect (Point or moment in time vs. time interval)"
     :type #fhir/code "Coding"}
    {:fhir/type :fhir.CodeSystem/property
     :code #fhir/code "SYSTEM"
     :uri #fhir/uri-interned "http://loinc.org/property/SYSTEM"
     :description #fhir/string "Fourth major axis-type of specimen or system: System (Sample) Type"
     :type #fhir/code "Coding"}
    {:fhir/type :fhir.CodeSystem/property
     :code #fhir/code "SCALE_TYP"
     :uri #fhir/uri-interned "http://loinc.org/property/SCALE_TYP"
     :description #fhir/string "Fifth major axis-scale of measurement: Type of Scale"
     :type #fhir/code "Coding"}
    {:fhir/type :fhir.CodeSystem/property
     :code #fhir/code "METHOD_TYP"
     :uri #fhir/uri-interned "http://loinc.org/property/METHOD_TYP"
     :description #fhir/string "Sixth major axis-method of measurement: Type of Method"
     :type #fhir/code "Coding"}
    {:fhir/type :fhir.CodeSystem/property
     :code #fhir/code "CLASS"
     :uri #fhir/uri-interned "http://loinc.org/property/CLASS"
     :description #fhir/string "An arbitrary classification of terms for grouping related observations together"
     :type #fhir/code "Coding"}
    {:fhir/type :fhir.CodeSystem/property
     :code #fhir/code "STATUS"
     :uri #fhir/uri-interned "http://loinc.org/property/STATUS"
     :description #fhir/string "Status of the term. Within LOINC, codes with STATUS=DEPRECATED are considered inactive. Current values: ACTIVE, TRIAL, DISCOURAGED, and DEPRECATED"
     :type #fhir/code "string"}
    {:fhir/type :fhir.CodeSystem/property
     :code #fhir/code "CLASSTYPE"
     :uri #fhir/uri-interned "http://loinc.org/property/CLASSTYPE"
     :description #fhir/string "1=Laboratory class; 2=Clinical class; 3=Claims attachments; 4=Surveys"
     :type #fhir/code "string"}
    {:fhir/type :fhir.CodeSystem/property
     :code #fhir/code "ORDER_OBS"
     :uri #fhir/uri-interned "http://loinc.org/property/ORDER_OBS"
     :description #fhir/string "Provides users with an idea of the intended use of the term by categorizing it as an order only, observation only, or both"
     :type #fhir/code "string"}]})

(defn- concept
  [code long-common-name component-pair property-pair time-pair system-pair
   scale-pair method-pair class-pair status class-type order-obs]
  (cond->
   {:system #fhir/uri-interned "http://loinc.org"
    :code (type/code code)
    :display (type/string long-common-name)
    :loinc/properties
    (cond->
     {:component component-pair
      :class class-pair
      :status status
      :class-type class-type}
      time-pair (assoc :time time-pair)
      system-pair (assoc :system system-pair)
      scale-pair (assoc :scale scale-pair)
      property-pair (assoc :property property-pair)
      method-pair (assoc :method method-pair)
      order-obs (assoc :order-obs order-obs))}
    (= :DEPRECATED status) (assoc :inactive #fhir/boolean true)))

(defn parse-class-type [class-type]
  (condp = (parse-long class-type)
    1 :laboratory-class
    2 :clinical-class
    3 :claims-attachments
    4 :surveys
    (ba/incorrect (format "Invalid class type `%s`." class-type))))

(defn parse-order-obs [order-obs]
  (condp = (str/lower-case order-obs)
    "observation" :observation
    "order" :order
    "both" :both
    "subset" :subset
    (ba/incorrect (format "Invalid order-obs value `%s`." order-obs))))

(defn- resource-as-stream [name]
  (let [classloader (.getContextClassLoader (Thread/currentThread))]
    (.getResourceAsStream classloader name)))

(defn- part-code-not-found-anom [part property]
  (ba/not-found (format "Part `%s` code `%s` not found." (name part) property)))

(defn- part-code [index part property]
  (get-in index [part property] (part-code-not-found-anom part property)))

(defn- part-pair [index part name]
  (when-not (or (str/blank? name) (= "-" (str/trim name)))
    (let [name (str/upper-case name)]
      (when-ok [code (part-code index part name)]
        [name code]))))

(defn- update-part-index [part-index [name code] concept]
  (-> (update part-index name (fnil conj []) concept)
      (update code (fnil conj []) concept)))

(defn- read-table [index]
  (when-let [in (resource-as-stream table)]
    (with-open [reader (io/reader in)]
      (reduce
       (fn [index
            [code component property time system scale method
             class _VersionLastChanged _CHNG_TYPE _DefinitionDescription status
             _CONSUMER_NAME class-type _FORMULA _EXMPL_ANSWERS
             _SURVEY_QUEST_TEXT _SURVEY_QUEST_SRC _UNITSREQUIRED _RELATEDNAMES2
             _SHORTNAME order-obs _HL7_FIELD_SUBFIELD_ID
             _EXTERNAL_COPYRIGHT_NOTICE _EXAMPLE_UNITS long-common-name
             _EXAMPLE_UCUM_UNITS _STATUS_REASON _STATUS_TEXT
             _CHANGE_REASON_PUBLIC _COMMON_TEST_RANK _COMMON_ORDER_RANK]]
         (if-ok [component-pair (part-pair index :component component)
                 property-pair (part-pair index :property property)
                 time-pair (part-pair index :time time)
                 system-pair (part-pair index :system system)
                 scale-pair (part-pair index :scale scale)
                 method-pair (part-pair index :method method)
                 class-pair (part-pair index :class class)
                 status (keyword status)
                 class-type (parse-class-type class-type)
                 order-obs (when-not (str/blank? order-obs) (parse-order-obs order-obs))]
           (let [concept (concept code long-common-name component-pair
                                  property-pair time-pair system-pair scale-pair
                                  method-pair class-pair status class-type
                                  order-obs)]
             (cond->
              (-> (assoc-in index [:concept-index code] concept)
                  (update :component-index update-part-index component-pair concept)
                  (update :class-index update-part-index class-pair concept)
                  (update-in [:status-index status] (fnil conj []) concept)
                  (update-in [:class-type-index class-type] (fnil conj []) concept))
               time-pair (update :time-index update-part-index time-pair concept)
               system-pair (update :system-index update-part-index system-pair concept)
               scale-pair (update :scale-index update-part-index scale-pair concept)
               property-pair (update :property-index update-part-index property-pair concept)
               method-pair (update :method-index update-part-index method-pair concept)
               order-obs (update-in [:order-obs-index order-obs] (fnil conj []) concept)))
           reduced))
       index
       (rest (csv/read-csv reader))))))

(defn- answer-concept [list-code code display]
  {:system #fhir/uri-interned "http://loinc.org"
   :code (type/code code)
   :display (type/string display)
   :loinc/properties
   {:list list-code}})

(defn- read-answer-lists [index]
  (when-let [in (resource-as-stream answer-lists)]
    (with-open [reader (io/reader in)]
      (transduce
       (remove (fn [[_id _display _oid extern]] (= "Y" extern)))
       (completing
        (fn [index [id name _oid _extern _ _ answer-id _LocalAnswerCode
                    _LocalAnswerCodeSystem _SequenceNumber answer-display]]
          (let [concept (answer-concept id answer-id answer-display)]
            (-> (assoc-in index [:concept-index answer-id] concept)
                (assoc-in [:value-sets id] {:title (type/string name)})
                (update-in [:value-set-concepts id] (fnil conj []) concept)))))
       index
       (rest (csv/read-csv reader))))))

(defn- read-parts [index]
  (when-let [in (resource-as-stream parts)]
    (with-open [reader (io/reader in)]
      (transduce
       (filter (fn [[_id type-name]] (#{"COMPONENT" "PROPERTY" "TIME" "SYSTEM" "SCALE" "METHOD" "CLASS"} type-name)))
       (completing
        (fn [index [id type-name name]]
          (assoc-in index [(keyword (str/lower-case type-name)) (str/upper-case name)] id)))
       index
       (rest (csv/read-csv reader))))))

(defn build []
  (when-ok [index {:code-systems [(code-system)]}
            index (read-parts index)
            index (read-table index)]
    (read-answer-lists index)))

(defn property-name-from-index [index]
  (condp = index
    :time-index "TIME_ASPCT"
    :scale-index "SCALE_TYP"
    :method-index "METHOD_TYP"
    (->> (-> index name (str/split #"-") butlast) (str/join "_") str/upper-case)))

(defn property-name-from-key [key]
  (condp = key
    :time "TIME_ASPCT"
    :scale "SCALE_TYP"
    :method "METHOD_TYP"
    (-> key name str/upper-case)))
