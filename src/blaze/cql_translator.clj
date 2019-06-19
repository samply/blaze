(ns blaze.cql-translator
  (:require
    [cheshire.core :as json]
    [cheshire.parse :refer [*use-bigdecimals?*]]
    [cognitect.anomalies :as anom])
  (:import
    [org.cqframework.cql.cql2elm CqlTranslator CqlTranslator$Options
                                 FhirLibrarySourceProvider LibraryManager ModelManager]))


(set! *warn-on-reflection* true)


(defn- options [locators?]
  (->> (cond-> [CqlTranslator$Options/EnableResultTypes]
         locators?
         (conj CqlTranslator$Options/EnableLocators))
       (into-array CqlTranslator$Options)))


(defn translate [cql & {:keys [locators?]}]
  (let [model-manager (ModelManager.)
        library-manager (LibraryManager. model-manager)
        _ (.registerProvider (.getLibrarySourceLoader library-manager) (FhirLibrarySourceProvider.))
        translator (CqlTranslator/fromText cql model-manager library-manager (options locators?))]
    (if-let [errors (seq (.getErrors translator))]
      {::anom/category ::anom/invalid
       ::anom/message (apply str (map ex-message errors))
       :cql cql
       :errors errors}
      (:library
        (binding [*use-bigdecimals?* true]
          (json/parse-string (.toJson translator) keyword))))))
