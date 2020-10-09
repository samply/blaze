(ns blaze.fhir.spec
  (:require
    [blaze.fhir.hash.spec]
    [blaze.fhir.spec.impl]
    [blaze.fhir.spec.type :as type]
    [clojure.alpha.spec :as s2]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [clojure.walk :as walk])
  (:import
    [java.util.regex Pattern]))


;; ---- Specs -----------------------------------------------------------------

(s/def :fhir.type/name
  (s/and string? #(re-matches #"[A-Z]([A-Za-z0-9_]){0,254}" %)))


(s/def :fhir/type
  (s/and
    keyword?
    #(some-> (namespace %) (str/starts-with? "fhir"))
    #(s/valid? :fhir.type/name (name %))))


(s/def :blaze.resource/id
  (s/and string? #(re-matches #"[A-Za-z0-9\-\.]{1,64}" %)))


(s/def :blaze.fhir/local-ref
  (s/and string?
         (s/conformer #(str/split % #"/" 2))
         (s/tuple :fhir.type/name :blaze.resource/id)))


(s/def :blaze/resource
  #(s2/valid? :fhir/Resource %))



;; ---- Functions -------------------------------------------------------------

(defn type-exists? [type]
  (some? (s2/get-spec (keyword "fhir" type))))


(defn valid-json?
  "Determines whether the resource is valid."
  {:arglists '([resource])}
  [{type :resourceType :as resource}]
  (if type
    (if-let [spec (s2/get-spec (keyword "fhir.json" type))]
      (s2/valid? spec resource)
      false)
    false))


(defn conform-json
  "Returns the internal representation of `resource` parsed from JSON."
  {:arglists '([resource])}
  [{type :resourceType :as resource}]
  (or
    (when type
      (when-let [spec (s2/get-spec (keyword "fhir.json" type))]
        (let [resource (s2/conform spec resource)]
          (when-not (s2/invalid? resource)
            resource))))
    ::s/invalid))


(defn conform-cbor
  "Returns the internal representation of `resource` parsed from CBOR."
  {:arglists '([resource])}
  [{type :resourceType :as resource}]
  (or
    (when type
      (when-let [spec (s2/get-spec (keyword "fhir.cbor" type))]
        (let [resource (s2/conform spec resource)]
          (when-not (s2/invalid? resource)
            resource))))
    ::s/invalid))


(defn conform-xml
  "Returns the internal representation of `resource` parsed from XML."
  {:arglists '([resource])}
  [{:keys [tag] :as resource}]
  (or
    (when type
      (when-let [spec (s2/get-spec (keyword "fhir.xml" (name tag)))]
        (let [resource (s2/conform spec resource)]
          (when-not (s2/invalid? resource)
            resource))))
    ::s/invalid))


(defn unform-json
  "Returns the JSON representation of `resource`."
  [resource]
  (let [key (keyword "fhir.json" (name (type/-type resource)))]
    (if-let [spec (s2/get-spec key)]
      (s2/unform spec resource)
      (throw (ex-info (format "Missing spec: %s" key) {:key key})))))


(defn unform-cbor
  "Returns the CBOR representation of `resource`."
  [resource]
  (let [key (keyword "fhir.cbor" (name (type/-type resource)))]
    (if-let [spec (s2/get-spec key)]
      (s2/unform spec resource)
      (throw (ex-info (format "Missing spec: %s" key) {:key key})))))


(defn unform-xml
  "Returns the XML representation of `resource`."
  [resource]
  (let [key (keyword "fhir.xml" (name (type/-type resource)))]
    (if-let [spec (s2/get-spec key)]
      (s2/unform spec resource)
      (throw (ex-info (format "Missing spec: %s" key) {:key key})))))


(defn fhir-type [x]
  (type/-type x))


(defn to-date-time [x]
  (type/-to-date-time x))


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
  "Returns a dot-separated string of all namespace and name parts of `key`,
  excluding the two leading parts."
  [key]
  (->> (drop 2 (conj (str/split (namespace key) #"\.") (name key)))
       (str/join ".")))


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
            (if (:tag val) (-> val :attrs :value) val) (name (last via)) regex)
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
  (cond->
    {:fhir.issues/severity "error"
     :fhir.issues/code "invariant"
     :fhir.issues/diagnostics (diagnostics-from-problem pred val via)}
    (:resourceType resource)
    (assoc :fhir.issues/expression (fhir-path in resource))))


(defn explain-data-json
  "Given a resource, which includes :resourceType key, returns nil
  if the resource is conform to the spec :fhir.json/<resourceType>,
  else a map with at least the keys :fhir/issues who's value is a collection of
  issue-maps to build a OperationsOutcome and ::problems whose value is a
  collection of problem-maps, where problem-map has at least :path :pred and
  :val keys describing the predicate and the value that failed at that path."
  {:arglists '([resource])}
  ([{type :resourceType :as resource}]
   (if type
     (if-let [spec (s2/get-spec (keyword "fhir.json" type))]
       (when-let [error (s2/explain-data spec resource)]
         (assoc
           error
           :fhir/issues
           (mapv #(generate-issue resource %) (::s/problems error))))
       {:fhir/issues
        [{:fhir.issues/severity "error"
          :fhir.issues/code "value"
          :fhir.issues/diagnostics (format "Unknown resource type `%s`." type)}]})
     {:fhir/issues
      [{:fhir.issues/severity "error"
        :fhir.issues/code "value"
        :fhir.issues/diagnostics
        "Given resource does not contain a :resourceType key."}]})))


(defn explain-data-xml
  "Given a resource as XML element, returns nil
  if the resource is conform to the spec :fhir.xml/<tag>,
  else a map with at least the keys :fhir/issues who's value is a collection of
  issue-maps to build a OperationsOutcome and ::problems whose value is a
  collection of problem-maps, where problem-map has at least :path :pred and
  :val keys describing the predicate and the value that failed at that path."
  {:arglists '([resource])}
  ([{:keys [tag] :as resource}]
   (if tag
     (if-let [spec (s2/get-spec (keyword "fhir.xml" (name tag)))]
       (when-let [error (s2/explain-data spec resource)]
         (assoc
           error
           :fhir/issues
           (mapv #(generate-issue resource %) (::s/problems error))))
       {:fhir/issues
        [{:fhir.issues/severity "error"
          :fhir.issues/code "value"
          :fhir.issues/diagnostics (format "Unknown resource type `%s`." (name tag))}]})
     {:fhir/issues
      [{:fhir.issues/severity "error"
        :fhir.issues/code "value"
        :fhir.issues/diagnostics
        "Given resource does not contain a :tag key."}]})))


(defn primitive?
  "Primitive FHIR type like `id`."
  [spec]
  (and (keyword? spec)
       (= "fhir" (namespace spec))
       (Character/isLowerCase ^char (first (name spec)))))


(defn primitive-val?
  ""
  [x]
  (when-let [fhir-type (fhir-type x)]
    (and (= "fhir" (namespace fhir-type))
         (Character/isLowerCase ^char (first (name fhir-type))))))
