(ns blaze.terminology-service.local.code-system.sct.util
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.system :as system]
   [blaze.terminology-service.local.code-system.sct.type :refer [parse-sctid]])
  (:import
   [java.time LocalDate]
   [java.time.format DateTimeFormatter]))

(set! *warn-on-reflection* true)

(def ^:const ^String url "http://snomed.info/sct")

(def module-only-version-pattern
  #"http\:\/\/snomed\.info\/sct\/(\d+)")

(defn module-version [version]
  (if-let [[_ module version] (re-find #"http\:\/\/snomed\.info\/sct\/(\d+)\/version\/(\d{8})" version)]
    [(parse-sctid module) (parse-sctid version)]
    (ba/incorrect (format "Incorrectly formatted SNOMED CT version `%s`." version))))

(defn- version-url [module-id date]
  (format "%s/%s/version/%s" url module-id date))

(defn create-code-system [module-id version title]
  (cond->
   {:fhir/type :fhir/CodeSystem
    :meta
    #fhir/Meta
     {:tag
      [#fhir/Coding
        {:system #fhir/uri-interned "https://samply.github.io/blaze/fhir/CodeSystem/AccessControl"
         :code #fhir/code "read-only"}]}
    :url (type/uri-interned url)
    :version (type/string (version-url module-id version))
    :status #fhir/code "active"
    :experimental #fhir/boolean false
    :date (type/dateTime (system/parse-date-time (str (LocalDate/parse (str version) DateTimeFormatter/BASIC_ISO_DATE))))
    :caseSensitive #fhir/boolean true
    :hierarchyMeaning #fhir/code "is-a"
    :versionNeeded #fhir/boolean false
    :content #fhir/code "not-present"
    :filter
    [{:fhir/type :fhir.CodeSystem/filter
      :code #fhir/code "concept"
      :description #fhir/string "Includes all concept ids that have a transitive is-a relationship with the code provided as the value."
      :operator [#fhir/code "is-a"]
      :value #fhir/string "A SNOMED CT code"}
     {:fhir/type :fhir.CodeSystem/filter
      :code #fhir/code "concept"
      :description #fhir/string "Includes all concept ids that have a transitive is-a relationship with the code provided as the value, excluding the code itself."
      :operator [#fhir/code "descendent-of"]
      :value #fhir/string "A SNOMED CT code"}]}
    title
    (assoc :title (type/string title))))
