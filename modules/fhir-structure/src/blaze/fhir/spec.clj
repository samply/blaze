(ns blaze.fhir.spec
  (:require
    [blaze.fhir.spec.impl]
    [clojure.alpha.spec :as s2]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]))


;; ---- Specs -----------------------------------------------------------------

(s/def :blaze.resource/resourceType
  (s/and string? #(re-matches #"[A-Z]([A-Za-z0-9_]){0,254}" %)))


(s/def :blaze.resource/id
  (s/and string? #(re-matches #"[A-Za-z0-9\-\.]{1,64}" %)))


(s/def :blaze.fhir/local-ref
  (s/and string?
         (s/conformer #(str/split % #"/" 2))
         (s/tuple :blaze.resource/resourceType :blaze.resource/id)))


(s/def :blaze.resource.meta/versionId
  string?)


(s/def :blaze.resource/meta
  (s/keys :opt-un [:blaze.resource.meta/versionId]))


(s/def :blaze/resource
  (s/keys :req-un [:blaze.resource/resourceType :blaze.resource/id]
          :opt-un [:blaze.resource/meta]))



;; ---- Functions -------------------------------------------------------------

(defn type-exists? [type]
  (some? (s2/get-spec (keyword "fhir" type))))


(defn valid?
  "Determines whether the resource is valid."
  {:arglists '([resource])}
  [{type :resourceType :as resource}]
  (if-let [spec (s2/get-spec (keyword "fhir" type))]
    (s2/valid? spec resource)
    false))


(defn explain-data
  {:arglists '([resource])}
  [{type :resourceType :as resource}]
  (when-let [spec (s2/get-spec (keyword "fhir" type))]
    (s2/explain-data spec resource)))


(defn child-specs
  "Returns a map of child specs of this spec where the keys are the keys found
  in a FHIR resource or complex data type and the values are either spec keys of
  FHIR types, `coll-of` forms of those or predicate symbols.

  Example:

   {:id clojure.core/string?
    :extension (clojure.alpha.spec/coll-of :fhir/Extension)}"
  [spec]
  (let [[_ [m]] (s2/form spec)] m))


(defn cardinality [spec]
  (if (and (sequential? spec) (= `s2/coll-of (first spec)))
    :many
    :one))


(defn choice?
  [spec]
  (and (sequential? spec) (= `s2/or (first spec))))


(defn choices
  "Takes an or-spec form and returns its content."
  [spec]
  ;; fancy stuff to get an clojure.lang.PersistentList
  (into (list) (map vec) (reverse (partition 2 (rest spec)))))


(defn type-spec [spec]
  (if (and (sequential? spec) (= `s2/coll-of (first spec)))
    (second spec)
    spec))


(defn primitive? [spec]
  (or (symbol? spec)
      (and (keyword? spec)
           (= "fhir" (namespace spec))
           (Character/isLowerCase ^char (first (name spec))))))


(defn form [spec]
  (s2/form spec))


(comment
  (into (sorted-map) (child-specs :fhir/Specimen))
  (choices (get (child-specs :fhir/Observation) :value))
  (get (child-specs :fhir/Quantity) :value)
  (child-specs :fhir/Patient)

  (cardinality `string?)
  (cardinality :fhir/Annotation)
  (cardinality :fhir/code)
  (cardinality `(s2/coll-of :fhir/Extension))

  (type-spec :fhir/Annotation)
  (type-spec `(s2/coll-of :fhir/Extension))

  (primitive? :fhir/code)
  (primitive? :fhir/Annotation)
  (primitive? `string?)

  (type-exists? "Annotation")
  (type-exists? "Annotatio")

  (s2/form :fhir/string)
  (s2/form :fhir/Observation)
  (s2/form :fhir/Patient)
  (s2/valid? :fhir/Observation {:focus ""})
  (s2/schema)
  )
