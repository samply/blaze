(ns blaze.cql-translator
  (:require
    [blaze.elm.spec]
    [cheshire.core :as json]
    [cheshire.parse :refer [*use-bigdecimals?*]]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom])
  (:import
    [org.cqframework.cql.cql2elm
     CqlTranslator CqlTranslator$Options
     FhirLibrarySourceProvider LibraryManager ModelManager
     ModelInfoProvider ModelInfoLoader]
    [javax.xml.bind JAXB]
    [org.hl7.elm_modelinfo.r1 ModelInfo]
    [org.hl7.elm.r1 VersionedIdentifier]))


(set! *warn-on-reflection* true)

(defn- load-model-info [name]
  (let [res (io/resource name)
        ^ModelInfo modelInfo (JAXB/unmarshal res, ^Class ModelInfo)
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


(s/fdef translate
  :args (s/cat :cql string? :opts (s/* some?))
  :ret :elm/library)

(defn translate
  "Translates `cql` library into am :elm/library.

  Returns an anomaly with category :cognitect.anomalies/incorrect in case of
  errors."
  [cql & {:keys [locators?]}]
  (let [model-manager (ModelManager.)
        library-manager (LibraryManager. model-manager)
        _ (.registerProvider (.getLibrarySourceLoader library-manager) (FhirLibrarySourceProvider.))
        translator (CqlTranslator/fromText cql model-manager library-manager (options locators?))]
    (if-let [errors (seq (.getErrors translator))]
      {::anom/category ::anom/incorrect
       ::anom/message (apply str (map ex-message errors))
       :cql cql
       :errors errors}
      (:library
        (binding [*use-bigdecimals?* true]
          (json/parse-string (.toJson translator) keyword))))))
