(ns blaze.cql-translator
  (:require
    [blaze.elm.spec]
    [clojure.java.io :as io]
    [cognitect.anomalies :as anom]
    [jsonista.core :as j])
  (:import
    [org.cqframework.cql.cql2elm
     CqlTranslator CqlTranslator$Options
     FhirLibrarySourceProvider LibraryManager ModelManager
     ModelInfoProvider ModelInfoLoader]
    [java.util Locale]
    [javax.xml.bind JAXB]
    [org.hl7.elm_modelinfo.r1 ModelInfo]
    [org.hl7.elm.r1 VersionedIdentifier]))


(set! *warn-on-reflection* true)


(defn- load-model-info [name]
  (let [res (io/resource name)
        ^ModelInfo modelInfo (JAXB/unmarshal res ^Class ModelInfo)
        id (doto (VersionedIdentifier.)
             (.setId (.getName modelInfo))
             (.setVersion (.getVersion modelInfo)))
        provider (reify ModelInfoProvider (load [_] modelInfo))]
    (ModelInfoLoader/registerModelInfoProvider id provider)))


(defn- options [locators?]
  (->> (cond-> [CqlTranslator$Options/EnableResultTypes]
         locators?
         (conj CqlTranslator$Options/EnableLocators))
       (into-array CqlTranslator$Options)))


;; Our special model info with Specimen context
(load-model-info "blaze/fhir-modelinfo-4.0.0.xml")


(def ^:private json-object-mapper
  (j/object-mapper
    {:decode-key-fn true
     :bigdecimals true}))


(defn translate
  "Translates `cql` library into am :elm/library.

  Returns an anomaly with category :cognitect.anomalies/incorrect in case of
  errors."
  [cql & {:keys [locators?]}]
  ;; TODO: Remove if https://github.com/cqframework/clinical_quality_language/issues/579 is solved
  (Locale/setDefault Locale/ENGLISH)
  (let [model-manager (ModelManager.)
        library-manager (LibraryManager. model-manager)
        _ (.registerProvider (.getLibrarySourceLoader library-manager) (FhirLibrarySourceProvider.))
        translator (CqlTranslator/fromText cql model-manager library-manager (options locators?))]
    (if-let [errors (seq (.getErrors translator))]
      {::anom/category ::anom/incorrect
       ::anom/message (apply str (map ex-message errors))
       :cql cql
       :errors errors}
      (:library (j/read-value (.toJson translator) json-object-mapper)))))
