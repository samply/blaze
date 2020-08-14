(ns blaze.fhir.spec
  (:require
    [blaze.fhir.spec.impl]
    [clojure.alpha.spec :as s2]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [clojure.walk :as walk])
  (:import
    [java.util.regex Pattern]))


;; ---- Specs -----------------------------------------------------------------

(s/def :blaze.resource/resourceType
  (s/and string? #(re-matches #"[A-Z]([A-Za-z0-9_]){0,254}" %)))


(s/def :blaze.resource/id
  #(s2/valid? :fhir/id %))


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
  (if type
    (if-let [spec (s2/get-spec (keyword "fhir" type))]
      (s2/valid? spec resource)
      false)
    false))


(defn- fhir-path-data
  "Given a vector `path` and some `data` structure containing maps and vectors,
  for which the entries in elem correspond to the keys and vector entries of the
  given data, returns a vector of the same length as path with all vector entries
  from data replaced by their indices."
  {:arglists '([path data])}
  [[elem & elems] data]
  (if (vector? data)
    (cons (first (keep-indexed #(when (= %2 elem) %1) data))
          (when elems (fhir-path-data elems elem)))
    (cons elem (if elems (fhir-path-data elems (get data elem)) []))))


(defn fhir-path
  "Given a vector `path` and a `resource`, returns the fhir-path as string for
  the given path."
  [path resource]
  (->> (fhir-path-data path resource)
       (reduce (fn [list elem] (if (keyword? elem)
                                 (conj list (name elem))
                                 (update list
                                         (dec (count list))
                                         str
                                         (format "[%d]" elem))))
               [])
       (str/join ".")))


(defn- get-regex
  "Returns the first regex pattern in `form`."
  [form]
  (walk/postwalk
    #(if (sequential? %)
       (some identity %)
       (when (= (type %) Pattern) %))
    form))


(defn- fhir-data-type
  "Returns the following namespace parts and name of `key` if the namespace of
  `key` is more than fhir. Otherwise returns just the name of `key`."
  [key]
  (if (str/starts-with? (namespace key) "fhir")
    (->> (rest (conj (str/split (namespace key) #"\.") (name key)))
         (str/join "." ))
    (name key)))


(defn- diagnostics-from-problem
  "Returns a diagnostics message from the given arguments `pred`, `val` and `via`.

  `pred`: A Function to evaluate the given `val`.
  `val`: Error Value.
  `via`: Vector-path of fhir types to the given error.

  If `pred` contains regex-patterns to check, the first one is included in
  diagnostics message."
  [pred val via]
  (if-let [regex (get-regex pred)]
    (format "Error on value `%s`. Expected type is `%s`, regex `%s`."
            val (name (last via)) regex)
    (if (= `coll? pred)
      (format "Error on value `%s`. Expected type is `JSON array`."
              val)
      (->> (fhir-data-type (last via))
           (format "Error on value `%s`. Expected type is `%s`."
                   val)))))


(defn- generate-issue
  "Returns an issue of type map for the given arguments."
  {:arglists '([resource problem])}
  [resource {:keys [val via in pred]}]
  {:fhir.issues/severity "error"
   :fhir.issues/code "invariant"
   :fhir.issues/diagnostics (diagnostics-from-problem pred val via)
   :fhir.issues/expression (fhir-path in resource)})


(defn explain-data
  "Given a resource, which includes :resourceType key, returns nil
  if the resource is conform to the spec :fhir/resourceType,
  else a map with at least the keys :fhir/issues who's value is a collection of
  issue-maps to build a OperationsOutcome and ::problems whose value is a
  collection of problem-maps, where problem-map has at least :path :pred and
  :val keys describing the predicate and the value that failed at that path."
  {:arglists '([resource])}
  ([{type :resourceType :as resource}]
   (if type
     (when-let
       [spec-error (when-let
                     [spec (s2/get-spec (keyword "fhir" type))]
                     (s2/explain-data spec resource))]
       (assoc
         spec-error
         :fhir/issues
         (mapv #(generate-issue resource %)
               (::s/problems spec-error))))
     {:fhir/issues
      [{:fhir.issues/severity "error"
        :fhir.issues/code "value"
        :fhir.issues/diagnostics
        "Given resource does not contain `resourceType` key!"}]})))


(defn child-specs
  "Returns a map of child specs of this spec where the keys are the keys found
  in a FHIR resource or complex data type and the values are either spec keys of
  FHIR types, `coll-of` forms of those or predicate symbols.

  Example:

   {:id clojure.core/string?
    :extension (clojure.alpha.spec/coll-of :fhir/Extension)}"
  [spec]
  (let [[_ [m]] (s2/form spec)] m))


(defn cardinality
  "Returns either :many or :one."
  [spec]
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


(defn primitive?
  "Primitive FHIR type like `id`."
  [spec]
  (and (keyword? spec)
       (= "fhir" (namespace spec))
       (Character/isLowerCase ^char (first (name spec)))))


(defn system?
  "System FHIR Type like `http://hl7.org/fhirpath/System.String`."
  [spec]
  (or (symbol? spec)
      (and (sequential? spec) (= `s2/and (first spec)))))


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

  (primitive? :fhir/id)
  (primitive? :fhir.Patient/id)
  (primitive? :fhir/code)
  (primitive? :fhir/Annotation)
  (primitive? `string?)

  (type-exists? "Annotation")
  (type-exists? "Annotatio")

  (s2/form :fhir/id)
  (s2/form :fhir/string)
  (s2/form :fhir/Observation)
  (s2/form :fhir/Patient)
  (primitive? (:id (child-specs :fhir/Patient)))
  (s2/valid? :fhir/Observation {:focus ""})
  (s2/schema)
  )
