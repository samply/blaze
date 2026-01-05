(ns blaze.fhir.util
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.fhir.spec.type :as type]
   [blaze.util :refer [str]]
   [clojure.string :as str]
   [cognitect.anomalies :as anom])
  (:import
   [blaze.fhir.spec.type Base]
   [com.google.common.base CaseFormat]
   [java.util Comparator]))

(set! *warn-on-reflection* true)

(defn parameters [& nvs]
  {:fhir/type :fhir/Parameters
   :parameter
   (into
    []
    (keep
     (fn [[name value]]
       (when (some? value)
         {:fhir/type :fhir.Parameters/parameter
          :name (type/string name)
          ;; TODO: improve resource detection
          (if (instance? Base value) :value :resource) value})))
    (partition 2 nvs))})

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

(defn- camel->kebab [s]
  (.to CaseFormat/LOWER_CAMEL CaseFormat/LOWER_HYPHEN s))

(defn- plural [s]
  (if (str/ends-with? s "y")
    (str (subs s 0 (dec (count s))) "ies")
    (str s "s")))

(defn- assoc-via [params {:keys [cardinality]} name value]
  (if (identical? :many cardinality)
    (update params (keyword (plural (camel->kebab name))) (fnil into []) (if (sequential? value) value [value]))
    (assoc params (keyword (camel->kebab name)) value)))

(defn- unsupported-parameter-anom [name]
  (ba/unsupported (format "Unsupported parameter `%s`." name) :http/status 400))

(defn coerce-params
  "Coerces parameters from a FHIR `parameters` resource according to `specs`.

  The `specs` argument is a map from parameter name to a specification map with
  the following keys:
   * :action      - one of :copy, :copy-complex-type or :copy-resource
   * :cardinality - :many if the parameter can appear multiple times
   * :coerce      - a function to coerce the value (only for :action :copy)

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
  `specs` that are not in `parameters` don't appear in the result.

  Returns the coerced map or an anomaly in case of coercion errors or unsupported
  parameters (if a parameter is in `specs` but the :action is missing or invalid)."
  {:arglists '([specs parameters])}
  [specs {params :parameter}]
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
