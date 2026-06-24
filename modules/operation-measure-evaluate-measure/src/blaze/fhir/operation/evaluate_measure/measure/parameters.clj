(ns blaze.fhir.operation.evaluate-measure.measure.parameters
  "Conversion of the FHIR `parameters` input of the $evaluate-measure operation
  into CQL parameter values.

  The conversion follows the type mapping of the Using CQL topic of the Clinical
  Reasoning Module. Currently the common primitive types (boolean, integer,
  decimal, string, code, date and dateTime) together with the repetition and
  parts semantics are supported:

  * a parameter that appears more than once becomes a CQL List,
  * a parameter with parts becomes a CQL Tuple.

  Unsupported types and parameter names that aren't declared in the library
  result in an anomaly."
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]))

(defn- unsupported-type-msg [name type]
  (if type
    (format "Unsupported type `%s` of parameter `%s`." (clojure.core/name type) name)
    (format "Unsupported type of parameter `%s`." name)))

(defn- unsupported-type-anom [name type]
  (ba/unsupported
   (unsupported-type-msg name type)
   :fhir/issue "not-supported"
   :fhir.issue/expression name))

(defn- unknown-parameter-anom [name]
  (ba/incorrect
   (format "Unknown parameter `%s`." name)
   :fhir/issue "value"
   :fhir.issue/expression name))

(defn- primitive-value
  "Converts the FHIR primitive `value` of the parameter named `name` into a CQL
  value.

  Returns an anomaly for unsupported types."
  [name value]
  (case (:fhir/type value)
    (:fhir/boolean :fhir/decimal :fhir/string :fhir/code :fhir/date :fhir/dateTime) (:value value)
    :fhir/integer (long (:value value))
    (unsupported-type-anom name (:fhir/type value))))

(declare elements->map)

(defn- element-value
  "Converts a single parameter `element` named `name` into a CQL value.

  An element with parts becomes a Tuple. An element with a primitive value is
  converted according to the type mapping. Returns an anomaly for unsupported
  types including resource-valued parameters."
  [name {:keys [value part resource]}]
  (cond
    (seq part) (elements->map keyword part)
    (some? value) (primitive-value name value)
    :else (unsupported-type-anom name (:fhir/type resource))))

(defn- group-value
  "Converts the parameter `elements` sharing the same `name` into a CQL value.

  A single element yields its value. More than one element yields a List."
  [name elements]
  (if (next elements)
    (reduce
     (fn [r element]
       (if-ok [value (element-value name element)]
         (conj r value)
         reduced))
     []
     elements)
    (element-value name (first elements))))

(defn- elements->map
  "Groups `elements` by parameter name and converts each group into a CQL value,
  keying the result map via `key-fn` applied to the name.

  Returns an anomaly if any conversion fails."
  [key-fn elements]
  (reduce
   (fn [r [name elements]]
     (if-ok [value (group-value name elements)]
       (assoc r (key-fn name) value)
       reduced))
   {}
   (group-by (comp :value :name) elements)))

(defn effective-parameters
  "Returns the effective CQL parameter map by converting the input FHIR
  `parameters` resource into CQL values and merging them over
  `parameter-default-values`.

  Supplied parameters override the library defaults by name while library
  defaults still apply for parameters not supplied. A parameter that appears
  more than once becomes a CQL List, a parameter with parts becomes a CQL Tuple.

  Returns an anomaly if a supplied parameter has an unsupported type or a name
  that isn't declared in the library."
  {:arglists '([parameter-default-values parameters])}
  [parameter-default-values {:keys [parameter]}]
  (when-ok [parameters (elements->map identity parameter)]
    (if-let [name (first (remove (set (keys parameter-default-values))
                                 (keys parameters)))]
      (unknown-parameter-anom name)
      (merge parameter-default-values parameters))))
