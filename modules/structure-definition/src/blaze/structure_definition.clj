(ns blaze.structure-definition
  (:require
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [taoensso.timbre :as log]))


;; ---- FHIR Element Definition -----------------------------------------------

(s/def :ElementDefinition/path
  string?)


(s/def :ElementDefinition/max
  string?)


(s/def :ElementDefinition.type/code
  string?)


(s/def :ElementDefinition.type/_code
  map?)


(s/def :ElementDefinition/type
  (s/coll-of
    (s/keys :req [:ElementDefinition.type/code])))


(s/def :ElementDefinition/isSummary
  boolean?)


(s/def :ElementDefinition.un/type
  (s/coll-of
    (s/keys :req-un [(or :ElementDefinition.type/code
                         :ElementDefinition.type/_code)])))


(s/def :ElementDefinition.binding/strength
  string?)


(s/def :ElementDefinition.binding/valueSet
  string?)


(s/def :ElementDefinition.un/binding
  (s/keys :req-un [:ElementDefinition.binding/strength]
          :opt-un [:ElementDefinition.binding/valueSet]))


(s/def :fhir/ElementDefinition
  (s/keys :req [:ElementDefinition/path]
          :opt [:ElementDefinition/max
                :ElementDefinition/type]))


(s/def :fhir.un/ElementDefinition
  (s/keys :req-un [:ElementDefinition/path]
          :opt-un [:ElementDefinition/max
                   :ElementDefinition.un/type
                   :ElementDefinition.un/binding]))



;; ---- FHIR Structure Definition ---------------------------------------------

(s/def :StructureDefinition/name
  string?)


(s/def :StructureDefinition/kind
  #{"primitive-type" "complex-type" "resource" "logical"})


(s/def :StructureDefinition.snapshot/element
  (s/coll-of :fhir/ElementDefinition))


(s/def :StructureDefinition.snapshot.un/element
  (s/coll-of :fhir.un/ElementDefinition))


(s/def :StructureDefinition/snapshot
  (s/keys :req [:StructureDefinition.snapshot/element]))


(s/def :StructureDefinition.un/snapshot
  (s/keys :req-un [:StructureDefinition.snapshot.un/element]))


(s/def :fhir/StructureDefinition
  (s/keys :req [:StructureDefinition/name
                :StructureDefinition/kind]
          :opt [:StructureDefinition/snapshot]))


(s/def :fhir.un/StructureDefinition
  (s/keys :req-un [:StructureDefinition/name
                   :StructureDefinition/kind]
          :opt-un [:StructureDefinition.un/snapshot]))

;; ---- Read ------------------------------------------------------------------

(defn- read-bundle
  "Reads a bundle from classpath named `resource-name`."
  [resource-name]
  (with-open [rdr (io/reader (io/resource resource-name))]
    (json/parse-stream rdr keyword)))


(defn- extract [kind bundle]
  (into
    []
    (comp
      (map :resource)
      (filter #(= kind (:kind %))))
    (:entry bundle)))


(defn read-structure-definitions []
  (let [package "blaze/fhir/r4/structure_definitions"]
    (into
      (extract "complex-type" (read-bundle (str package "/profiles-types.json")))
      (into
        []
        (remove #(= "Parameters" (:name %)))
        (extract "resource" (read-bundle (str package "/profiles-resources.json")))))))


(defmethod ig/init-key :blaze/structure-definition
  [_ _]
  (let [structure-definitions (read-structure-definitions)]
    (log/info "Read structure definitions resulting in:"
              (count structure-definitions) "structure definitions")
    structure-definitions))
