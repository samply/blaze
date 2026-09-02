(ns blaze.fhir.util
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.fhir.spec.type :as type]
   [blaze.util :refer [str]]
   [clojure.string :as str]
   [cognitect.anomalies :as anom])
  (:import
   [blaze.fhir.spec.type Base]
   [com.google.common.base CaseFormat]
   [java.util Comparator]))

(set! *warn-on-reflection* true)

(declare parameter*)

(defn- parameter** [name value]
  [{:fhir/type :fhir.Parameters/parameter
    :name (type/string name)

    ;; TODO: improve resource detection
    (cond (instance? Base value) :value
          (sequential? value) :part
          :else :resource)
    (if (sequential? value)
      (into [] (mapcat parameter*) (partition 2 value))
      value)}])

(defn- parameter* [[name value]]
  (cond
    (nil? value) []
    (sequential? value) (mapcat #(parameter** name %) value)
    :else (parameter** name value)))

(defn parameter
  "Builds a single FHIR `Parameters.parameter` entry from `name` and `value`.

  The shape of `value` determines which field it populates:

  * a FHIR datatype value (anything satisfying `Base`, like `#fhir/string \"x\"`
    or `#fhir/Quantity{...}`) populates `parameter.value[x]`,
  * a non-datatype map (a resource) populates `parameter.resource`,
  * a sequential value populates `parameter.part`, where the sequence is read as
    the name/value pairs of the parts (recursively following the same rules)."
  [name value]
  (first (parameter** name value)))

(defn parameters
  "Builds a FHIR `Parameters` resource from alternating `name`/`value` pairs.

  Each value is mapped to a `parameter` entry by the same rules as `parameter`,
  with one addition: a sequential value denotes repetition. It produces one
  `parameter` entry per element, all sharing the same `name`, and each element
  is mapped by the same rules. Hence:

  * `(parameters \"x\" #fhir/string\"a\")` yields a single value parameter,
  * `(parameters \"x\" [#fhir/string\"a\" #fhir/string\"b\"])` yields the
    parameter `x` repeated twice,
  * `(parameters \"x\" [[\"a\" #fhir/integer 1 \"b\" #fhir/integer 2]])` yields
    a single parameter `x` with the parts `a` and `b`, because the one
    repetition element is itself sequential.

  A `nil` value is skipped."
  [& nvs]
  {:fhir/type :fhir/Parameters
   :parameter (into [] (mapcat parameter*) (partition 2 nvs))})

(def subsetted
  "SUBSETTED Coding"
  #fhir/Coding
   {:system #fhir/uri-interned "http://terminology.hl7.org/CodeSystem/v3-ObservationValue"
    :code #fhir/code "SUBSETTED"})

(defn subsetted?
  "Checks whether `coding` is a SUBSETTED coding."
  {:arglists '([coding])}
  [{{system-value :value} :system {code-value :value} :code}]
  (and (= "http://terminology.hl7.org/CodeSystem/v3-ObservationValue" system-value)
       (= "SUBSETTED" code-value)))

(defn- nat-cmp [^Comparable x y]
  (.compareTo x y))

(defn version-cmp
  "Compares two version numbers."
  [v1 v2]
  (loop [[p1 & p1s] (some-> v1 (str/split #"\."))
         [p2 & p2s] (some-> v2 (str/split #"\."))]
    (if p1
      (if p2
        (let [n1 (parse-long p1)
              n2 (parse-long p2)]
          (if n1
            (if n2
              (let [r (nat-cmp n1 n2)]
                (if (zero? r)
                  (recur p1s p2s)
                  r))
              -1)
            (if n2
              1
              (let [r (nat-cmp p1 p2)]
                (if (zero? r)
                  (recur p1s p2s)
                  r)))))
        1)
      (if p2
        -1
        0))))

(defn- t [resource]
  (:blaze.db/t (:blaze.db/tx (meta resource))))

(def ^:private priority-cmp
  (-> (Comparator/comparing #(-> % :status :value) (Comparator/nullsFirst (.reversed (Comparator/naturalOrder))))
      (.thenComparing #(-> % :version :value) version-cmp)
      (.thenComparing t (Comparator/nullsFirst (Comparator/naturalOrder)))
      (.thenComparing #(% :id) (Comparator/naturalOrder))
      (.reversed)))

(defn sort-by-priority
  "Sorts `resources` by:
   * status
   * version
   * t
   * id"
  [resources]
  (sort priority-cmp resources))

(defn split-canonical
  "Splits `canonical` at the first `|` into a vector of the URL and the version,
  following the `url|version` canonical reference syntax (FHIR R4 §2.3.0.5).

  The version is omitted if `canonical` doesn't contain a `|` or the part after
  it is blank, because a blank version is equivalent to omitting it (FHIR R4
  §3.1.1.3).

  Example: \"url|1.2.3\" -> [\"url\" \"1.2.3\"]
           \"url\"       -> [\"url\"]"
  [canonical]
  (let [[url version] (str/split canonical #"\|" 2)]
    (cond-> [url] (not (str/blank? version)) (conj version))))

(defn- camel->kebab [s]
  (.to CaseFormat/LOWER_CAMEL CaseFormat/LOWER_HYPHEN s))

(defn- plural [s]
  (if (str/ends-with? s "y")
    (str (subs s 0 (dec (count s))) "ies")
    (str s "s")))

(defn coerce-integer
  "Returns the int value of the integer `x`.

  Returns an anomaly if `x` isn't an integer or has no value."
  [x]
  (if (type/integer? x)
    (if-some [value (:value x)]
      value
      (ba/incorrect "Missing value."))
    (ba/incorrect "Has to be an integer.")))

(defn coerce-boolean
  "Returns the boolean value of the boolean `x`.

  Returns an anomaly if `x` isn't a boolean or has no value."
  [x]
  (if (type/boolean? x)
    (if-some [value (:value x)]
      value
      (ba/incorrect "Missing value."))
    (ba/incorrect "Has to be a boolean.")))

(defn coerce-string
  "Returns the string value of the string `x`.

  Returns an anomaly if `x` isn't a string or has no value."
  [x]
  (if (type/string? x)
    (if-some [value (:value x)]
      value
      (ba/incorrect "Missing value."))
    (ba/incorrect "Has to be a string.")))

(defn coerce-uri
  "Returns the string value of `x`.

  Accepts any FHIR type with a string-valued value, not just uri, for
  robustness reasons.

  Returns an anomaly if `x` doesn't have a string value."
  [x]
  (if-some [value (:value x)]
    (if (string? value)
      value
      (ba/incorrect "Has to be a uri."))
    (ba/incorrect "Missing value.")))

(defn- assoc-via [params {:keys [cardinality]} name value]
  (if (identical? :many cardinality)
    (update params (keyword (plural (camel->kebab name))) (fnil into []) (if (sequential? value) value [value]))
    (assoc params (keyword (camel->kebab name)) value)))

(defn- unsupported-parameter-anom [name]
  (ba/unsupported (format "Unsupported parameter `%s`." name) :http/status 400))

(defn- coerce-params* [specs params]
  (reduce
   (fn [new-params {{name :value} :name :as param}]
     (if-let [{:keys [action] :as spec} (specs name)]
       (case action
         :copy
         (if-ok [value ((:coerce spec :value) (:value param))]
           (assoc-via new-params spec name value)
           (fn [e]
             (update e ::anom/message (partial str (format "Invalid value for parameter `%s`. " name)))))

         :copy-complex-type
         (assoc-via new-params spec name (:value param))

         :copy-resource
         (assoc-via new-params spec name (:resource param))

         (reduced (unsupported-parameter-anom name)))
       new-params))
   {}
   params))

(defn- missing-parameter-anom [name]
  (ba/incorrect (format "Missing required parameter `%s`." name) :http/status 400))

(defn- check-required-params [specs params]
  (let [present (into #{} (keep (comp :value :name)) params)]
    (reduce-kv
     (fn [_ name {:keys [required]}]
       (when (and required (not (contains? present name)))
         (reduced (missing-parameter-anom name))))
     nil
     specs)))

(defn coerce-params
  "Coerces parameters from a FHIR `parameters` resource according to `specs`.

  The `specs` argument is a map from parameter name to a specification map with
  the following keys:
   * :action      - one of :copy, :copy-complex-type or :copy-resource
   * :cardinality - :many if the parameter can appear multiple times
   * :coerce      - a function to coerce the value (only for :action :copy)
   * :required    - true if the parameter must be present

  The :action determines how the value is extracted:
   * :copy              - uses the value of the parameter (e.g. valueString)
   * :copy-complex-type - uses the value of the parameter (e.g. valueCoding)
   * :copy-resource     - uses the resource of the parameter

  If :coerce is given, the value is passed to that function. If the function
  returns an anomaly, the processing stops and the anomaly is returned.

  The keys of the resulting map are the kebab-cased parameter names. If
  :cardinality is :many, the key is pluralized and the values are collected in
  a vector. If the coerced value is sequential, it is flattened into the vector.

  Parameters in `parameters` that are not in `specs` are ignored. Parameters in
  `specs` that are not in `parameters` don't appear in the result, unless they
  are marked :required, in which case an anomaly is returned.

  Returns the coerced map or an anomaly in case of coercion errors, missing
  required parameters or unsupported parameters (if a parameter is in `specs` but
  the :action is missing or invalid)."
  {:arglists '([specs parameters])}
  [specs {params :parameter}]
  (when-ok [new-params (coerce-params* specs params)
            _ (check-required-params specs params)]
    new-params))
