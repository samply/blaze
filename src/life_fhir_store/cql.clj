(ns life-fhir-store.cql
  (:require
    [cheshire.core :as json]
    [cognitect.anomalies :as anom])
  (:import
    [org.cqframework.cql.cql2elm CqlTranslator CqlTranslator$Options
                                 FhirLibrarySourceProvider LibraryManager ModelManager]))

(def ^:private options
  (into-array CqlTranslator$Options []))

(defn translate [cql]
  (let [model-manager (ModelManager.)
        library-manager (LibraryManager. model-manager)
        _ (.registerProvider (.getLibrarySourceLoader library-manager) (FhirLibrarySourceProvider.))
        translator (CqlTranslator/fromText cql model-manager library-manager options)]
    (if-let [errors (seq (.getErrors translator))]
      {::anom/category ::anom/fault
       ::anom/message (apply str (map #(.getMessage ^Exception %) errors))
       :errors errors}
      (:library (json/parse-string (.toJson translator) keyword)))))
