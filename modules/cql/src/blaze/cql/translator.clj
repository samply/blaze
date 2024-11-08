(ns blaze.cql.translator
  (:require
   [blaze.anomaly :as ba]
   [blaze.elm.spec]
   [jsonista.core :as j])
  (:import
   [org.cqframework.cql.cql2elm
    CqlTranslator CqlTranslatorOptions$Options LibraryManager ModelManager]
   [org.cqframework.cql.cql2elm.quick FhirLibrarySourceProvider]))

(set! *warn-on-reflection* true)

(def ^:private options
  (into-array [CqlTranslatorOptions$Options/EnableResultTypes]))

(def ^:private json-object-mapper
  (j/object-mapper
   {:decode-key-fn true
    :bigdecimals true}))

(defn- parse-library [^CqlTranslator translator]
  (try
    (:library (j/read-value (.toJson translator) json-object-mapper))
    (catch Exception e
      (ba/fault (str "Error while parsing the ELM representation of a CQL library: " (ex-message e))))))

(defn translate
  "Translates `cql` library into am :elm/library.

  Returns an anomaly in case of errors."
  [cql]
  (let [model-manager (ModelManager.)
        library-manager (LibraryManager. model-manager)
        _ (.registerProvider (.getLibrarySourceLoader library-manager) (FhirLibrarySourceProvider.))
        translator (CqlTranslator/fromText cql model-manager library-manager options)]
    (if-let [errors (seq (.getErrors translator))]
      (ba/incorrect
       (apply str (map ex-message errors))
       :cql cql
       :errors errors)
      (parse-library translator))))
