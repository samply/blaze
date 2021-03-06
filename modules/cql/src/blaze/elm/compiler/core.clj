(ns blaze.elm.compiler.core
  (:require
    [blaze.elm.protocols :as p]
    [blaze.fhir.spec.type.system :as system]
    [clojure.string :as str]
    [cuerdas.core :as cuerdas])
  (:import
    [java.time.temporal ChronoUnit]))


(set! *warn-on-reflection* true)


(defprotocol Expression
  (-eval [this context resource scope]))


(defn expr? [x]
  (satisfies? Expression x))


(extend-protocol Expression
  nil
  (-eval [this _ _ _]
    this)

  Object
  (-eval [this _ _ _]
    this))


(defn static? [x]
  (not (instance? blaze.elm.compiler.core.Expression x)))


(defmulti compile*
  "Compiles `expression` in `context`."
  {:arglists '([context expression])}
  (fn [_ {:keys [type] :as expr}]
    (assert (string? type) (format "Missing :type in expression `%s`." (pr-str expr)))
    (keyword "elm.compiler.type" (cuerdas/kebab type))))


(defmethod compile* :default
  [_ {:keys [type]}]
  (throw (Exception. (str "Unsupported ELM expression type: " (or type "<missing>")))))


(defn to-chrono-unit [precision]
  (case (str/lower-case precision)
    "year" ChronoUnit/YEARS
    "month" ChronoUnit/MONTHS
    "week" ChronoUnit/WEEKS
    "day" ChronoUnit/DAYS
    "hour" ChronoUnit/HOURS
    "minute" ChronoUnit/MINUTES
    "second" ChronoUnit/SECONDS
    "millisecond" ChronoUnit/MILLIS))


(defn append-locator [msg locator]
  (if locator
    (str msg " " locator ".")
    (str msg ".")))


(extend-protocol p/Equal
  Object
  (equal [x y]
    (system/equals x y)))


(extend-protocol p/Equivalent
  Object
  (equivalent [x y]
    (= x y)))


(extend-protocol p/Greater
  Comparable
  (greater [x y]
    (some->> y (.compareTo x) (< 0))))


(extend-protocol p/GreaterOrEqual
  Comparable
  (greater-or-equal [x y]
    (some->> y (.compareTo x) (<= 0))))


(extend-protocol p/Less
  Comparable
  (less [x y]
    (some->> y (.compareTo x) (> 0))))


(extend-protocol p/LessOrEqual
  Comparable
  (less-or-equal [x y]
    (some->> y (.compareTo x) (>= 0))))


(extend-protocol p/ToString
  Object
  (to-string [x]
    (str x)))
