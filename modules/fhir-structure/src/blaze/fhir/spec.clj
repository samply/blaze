(ns blaze.fhir.spec
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.fhir.hash.spec]
   [blaze.fhir.spec.impl :as impl]
   [blaze.fhir.spec.resource :as res]
   [blaze.fhir.spec.spec]
   [blaze.util :refer [str]]
   [clojure.alpha.spec :as s2]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.walk :as walk]
   [cognitect.anomalies :as anom])
  (:import
   [blaze.fhir.spec.type Primitive]
   [java.io ByteArrayOutputStream]
   [java.nio.charset StandardCharsets]
   [java.util.regex Pattern]))

(set! *warn-on-reflection* true)

(defn parse-json
  "Parses a complex value from JSON `source`.

  For resources, the two-arity version can be used. In this case the
  `resourceType` JSON property is used to determine the `type`.

  For complex types, the `type` has to be given.

  Returns an anomaly in case of errors."
  ([context source]
   (ba/try-all ::anom/incorrect (res/parse-json context source)))
  ([context type source]
   (ba/try-all ::anom/incorrect (res/parse-json context type source))))

(defn parse-cbor
  "Parses a complex value of `type` from `source`.

  Returns an anomaly in case of errors."
  ([context type source]
   (parse-cbor context type source :complete))
  ([context type source variant]
   (ba/try-all ::anom/incorrect (res/parse-cbor context type variant source))))

(defn type-exists? [type]
  (some? (s2/get-spec (keyword "fhir" type))))

(defn primitive-val?
  "Returns true if `x` is a primitive FHIR value."
  [x]
  (instance? Primitive x))

(defn write-json
  "Writes `value` to output stream `out` closing it if done."
  [context out value]
  (res/write-json context out value))

(defn write-json-as-bytes
  [context value]
  (let [out (ByteArrayOutputStream.)]
    (when-ok [_ (write-json context out value)]
      (.toByteArray out))))

(defn write-json-as-string
  [context value]
  (when-ok [bytes (write-json-as-bytes context value)]
    (String. ^bytes bytes StandardCharsets/UTF_8)))

(defn write-cbor
  [context x]
  (let [out (ByteArrayOutputStream.)]
    (when-ok [_ (res/write-cbor context out x)]
      (.toByteArray out))))

(defn unform-xml
  "Returns the XML representation of `resource`."
  [resource]
  (let [key (keyword "fhir.xml" (name (:fhir/type resource)))]
    (if-let [spec (s2/get-spec key)]
      (s2/unform spec resource)
      (throw (ex-info (format "Missing spec: %s" key) {:key key})))))

(defn- fhir-path-data
  "Given a vector `path` and some `data` structure containing maps and vectors,
  for which the entries in elem correspond to the keys and vector entries of the
  given data, returns a vector of the same length as path with all vector entries
  from data replaced by their indices."
  {:arglists '([path data])}
  [[elem & elems] data]
  (if (keyword? elem)
    (cons elem (if elems (fhir-path-data elems (get data elem)) []))
    (cons (first (keep-indexed #(when (= %2 elem) %1) (impl/ensure-coll data)))
          (when elems (fhir-path-data elems elem)))))

(defn fhir-path
  "Given a vector `path` and a `resource`, returns the fhir-path as string for
  the given path."
  [path resource]
  (->> (fhir-path-data path resource)
       (reduce
        (fn [list elem]
          (if (keyword? elem)
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

(defn- issue [pred val via]
  {:fhir.issues/severity "error"
   :fhir.issues/code "invariant"
   :fhir.issues/diagnostics (diagnostics-from-problem pred val via)})

(defn- generate-issue
  "Returns an issue of type map for the given arguments."
  {:arglists '([resource problem])}
  [resource {:keys [val via in pred]}]
  (cond-> (issue pred val via)
    (:resourceType resource)
    (assoc :fhir.issues/expression (fhir-path in resource))))

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
        :fhir.issues/diagnostics (format "Invalid resource element `%s`." resource)}]})))

(defn conform-xml
  "Returns the internal representation of `x` parsed from XML.

  Returns an anomaly if `x` isn't a valid intermediate representation of a
  resource."
  {:arglists '([x])}
  [{:keys [tag] :as x}]
  (or
   (when tag
     (when-let [spec (s2/get-spec (keyword "fhir.xml" (name tag)))]
       (let [resource (s2/conform spec x)]
         (when-not (s2/invalid? resource)
           resource))))
   (ba/incorrect
    "Invalid XML representation of a resource."
    :x x
    :fhir/issues (:fhir/issues (explain-data-xml x)))))
