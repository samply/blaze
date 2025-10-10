(ns blaze.fhir.util
  (:require
   [blaze.fhir.spec.type :as type]
   [clojure.string :as str])
  (:import
   [blaze.fhir.spec.type Base]
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
