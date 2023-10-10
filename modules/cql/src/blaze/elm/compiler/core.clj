(ns blaze.elm.compiler.core
  (:require
    [blaze.elm.protocols :as p]
    [blaze.fhir.spec.type.system :as system]
    [clojure.string :as str]
    [cuerdas.core :as c-str])
  (:import
    [clojure.lang IReduceInit]
    [java.time.temporal ChronoUnit]))


(set! *warn-on-reflection* true)


(defprotocol Expression
  (-static [expression])
  (-attach-cache [expression cache])
  (-patient-count [expression])
  (-resolve-refs [expression expression-defs])
  (-resolve-params [expression parameters])
  (-eval [expression context resource scope]
    "Evaluates `expression` on `resource` using `context` and optional `scope`
    for scoped expressions inside queries.")
  (-form [expression]))


(defn expr? [x]
  (satisfies? Expression x))


(defn static? [x]
  (-static x))


(extend-protocol Expression
  nil
  (-static [_]
    true)
  (-attach-cache [expr _]
    expr)
  (-patient-count [_]
    0)
  (-resolve-refs [expr _]
    expr)
  (-resolve-params [expr _]
    expr)
  (-eval [expr _ _ _]
    expr)
  (-form [_]
    'nil)

  Object
  (-static [_]
    true)
  (-attach-cache [expr _]
    expr)
  (-patient-count [_]
    0)
  (-resolve-refs [expr _]
    expr)
  (-resolve-params [expr _]
    expr)
  (-eval [expr _ _ _]
    expr)
  (-form [expr]
    expr)

  IReduceInit
  (-static [_]
    true)
  (-attach-cache [expr _]
    expr)
  (-patient-count [_]
    0)
  (-resolve-refs [expr _]
    expr)
  (-resolve-params [expr _]
    expr)
  (-eval [expr _ _ _]
    expr)
  (-form [expr]
    (mapv -form expr)))


(defmulti compile*
  "Compiles `expression` in `context`.

  Context consists of:
  * :library - the library in it's ELM form
  * :node - the database node"
  {:arglists '([context expression])}
  (fn [_ {:keys [type] :as expr}]
    (assert (string? type) (format "Missing :type in expression `%s`." (pr-str expr)))
    (keyword "elm.compiler.type" (c-str/kebab type))))


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
  (to-string [_]))
