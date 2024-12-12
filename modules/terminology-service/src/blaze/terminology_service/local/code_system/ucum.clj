(ns blaze.terminology-service.local.code-system.ucum
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system.core :as c]
   [blaze.terminology-service.local.code-system.util :as u]
   [cognitect.anomalies :as anom])
  (:import
   [org.fhir.ucum UcumEssenceService UcumService]))

(set! *warn-on-reflection* true)

(def ^:private service
  (let [classloader (.getContextClassLoader (Thread/currentThread))]
    (with-open [stream (.getResourceAsStream classloader "ucum-essence.xml")]
      (UcumEssenceService. stream))))

(defn- validate [code]
  (when (.validate ^UcumService service code)
    (ba/incorrect (format "The provided code `%s` was not found in the code system with URL `http://unitsofmeasure.org`." code))))

(defmethod c/validate-code :ucum
  [{:keys [url]} {:keys [request]}]
  (if-ok [code (u/extract-code request (type/value url))
          _ (validate code)]
    {:fhir/type :fhir/Parameters
     :parameter
     [(u/parameter "result" #fhir/boolean true)
      (u/parameter "code" (type/code code))
      (u/parameter "system" #fhir/uri"http://unitsofmeasure.org")]}
    (fn [{::anom/keys [message]}]
      {:fhir/type :fhir/Parameters
       :parameter
       [(u/parameter "result" #fhir/boolean false)
        (u/parameter "message" (type/string message))]})))
