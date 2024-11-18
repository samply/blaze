(ns blaze.elm.compiler.core
  (:refer-clojure :exclude [str])
  (:require
   [blaze.elm.protocols :as p]
   [blaze.elm.util :as elm-util]
   [blaze.fhir.spec.type.system :as system]
   [blaze.util :refer [str]]
   [clojure.string :as str])
  (:import
   [clojure.lang IReduceInit]
   [java.time.temporal ChronoUnit]))

(set! *warn-on-reflection* true)

(defprotocol Expression
  (-static [expression])
  (-attach-cache [expression cache]
    "Attaches `cache` to `expression` so the expression can obtain a Bloom filter.

    Returns a vector with a function as first element that will return tuple of
    expression and possible list of attached Bloom filters if called with no
    argument.

    If the Bloom filter is available, returns a new expression holding the Bloom
    filter, so it can be used to increase evaluation performance.

    Expressions that don't like to obtain a Bloom filter should call
    `-attach-cache` on it's operands in order to allow them to possible obtain a
    Bloom filter.")
  (-patient-count [expression]
    "Returns the number of patients from an attached Bloom filter. That patient
    count can be used by other expressions (most likely and/or/case) to
    reorder their operands so that expressions with less patients get evaluated
    first. Returns nil if unknown.")
  (-resolve-refs [expression expression-defs]
    "Resolves expressions defined in `expression-defs` in `expression`.")
  (-resolve-params [expression parameters])
  (-optimize [expression db])
  (-eval [expression context resource scope]
    "Evaluates `expression` on `resource` using `context` and optional `scope`
    for scoped expressions inside queries.")
  (-form [expression]))

(defn expr? [x]
  (satisfies? Expression x))

(defn static? [x]
  (-static x))

(defn attach-cache-expressions [cache expressions]
  (reduce
   (fn [[expressions bfs] expression]
     (let [[expression expression-bfs] ((first (-attach-cache expression cache)))]
       [(conj expressions expression) (into bfs expression-bfs)]))
   [[] []]
   expressions))

(defn attach-cache-helper-list [constructor cache ops]
  (let [[ops bfs] (attach-cache-expressions cache ops)]
    [(fn [] [(constructor ops) bfs])]))

(defn attach-cache-helper
  ([constructor cache op]
   (let [[op op-bfs] ((first (-attach-cache op cache)))]
     [(fn [] [(constructor op) op-bfs])]))
  ([constructor cache op-1 op-2]
   (let [[op-1 op-1-bfs] ((first (-attach-cache op-1 cache)))
         [op-2 op-2-bfs] ((first (-attach-cache op-2 cache)))]
     [(fn [] [(constructor op-1 op-2) (into op-1-bfs op-2-bfs)])]))
  ([constructor cache op-1 op-2 op-3]
   (let [[op-1 op-1-bfs] ((first (-attach-cache op-1 cache)))
         [op-2 op-2-bfs] ((first (-attach-cache op-2 cache)))
         [op-3 op-3-bfs] ((first (-attach-cache op-3 cache)))]
     [(fn []
        [(constructor op-1 op-2 op-3)
         (into [] cat [op-1-bfs op-2-bfs op-3-bfs])])]))
  ([constructor cache op-1 op-2 op-3 & more]
   (let [[op-1 op-1-bfs] ((first (-attach-cache op-1 cache)))
         [op-2 op-2-bfs] ((first (-attach-cache op-2 cache)))
         [op-3 op-3-bfs] ((first (-attach-cache op-3 cache)))
         [ops bfs] (attach-cache-expressions cache more)]
     [(fn []
        [(apply constructor op-1 op-2 op-3 ops)
         (into [] cat [op-1-bfs op-2-bfs op-3-bfs bfs])])])))

(defn attach-cache-helper-1
  [constructor cache op arg]
  (let [[op op-bfs] ((first (-attach-cache op cache)))]
    [(fn [] [(constructor op arg) op-bfs])]))

(defn attach-cache-helper-2
  ([constructor cache op arg-1 arg-2]
   (let [[op op-bfs] ((first (-attach-cache op cache)))]
     [(fn [] [(constructor op arg-1 arg-2) op-bfs])]))
  ([constructor cache op-1 op-2 arg-1 arg-2]
   (let [[op-1 op-1-bfs] ((first (-attach-cache op-1 cache)))
         [op-2 op-2-bfs] ((first (-attach-cache op-2 cache)))]
     [(fn [] [(constructor op-1 op-2 arg-1 arg-2) (into op-1-bfs op-2-bfs)])])))

(defn resolve-refs-helper
  ([constructor expression-defs op]
   (constructor (-resolve-refs op expression-defs)))
  ([constructor expression-defs op-1 op-2]
   (constructor (-resolve-refs op-1 expression-defs)
                (-resolve-refs op-2 expression-defs)))
  ([constructor expression-defs op-1 op-2 op-3]
   (constructor (-resolve-refs op-1 expression-defs)
                (-resolve-refs op-2 expression-defs)
                (-resolve-refs op-3 expression-defs))))

(defn resolve-refs-helper-1
  [constructor expression-defs op-1 op-2 arg]
  (constructor (-resolve-refs op-1 expression-defs)
               (-resolve-refs op-2 expression-defs)
               arg))

(defn resolve-refs-helper-2
  [constructor expression-defs op-1 op-2 arg-1 arg-2]
  (constructor (-resolve-refs op-1 expression-defs)
               (-resolve-refs op-2 expression-defs)
               arg-1 arg-2))

(defn resolve-params-helper
  ([constructor parameters op]
   (constructor (-resolve-params op parameters)))
  ([constructor parameters op-1 op-2]
   (constructor (-resolve-params op-1 parameters)
                (-resolve-params op-2 parameters)))
  ([constructor parameters op-1 op-2 op-3]
   (constructor (-resolve-params op-1 parameters)
                (-resolve-params op-2 parameters)
                (-resolve-params op-3 parameters))))

(defn resolve-params-helper-1
  [constructor parameters op-1 op-2 arg]
  (constructor (-resolve-params op-1 parameters)
               (-resolve-params op-2 parameters)
               arg))

(defn resolve-params-helper-2
  [constructor parameters op-1 op-2 arg-1 arg-2]
  (constructor (-resolve-params op-1 parameters)
               (-resolve-params op-2 parameters)
               arg-1 arg-2))

(defn optimize-helper
  ([constructor db op]
   (constructor (-optimize op db)))
  ([constructor db op-1 op-2]
   (constructor (-optimize op-1 db) (-optimize op-2 db)))
  ([constructor db op-1 op-2 op-3]
   (constructor (-optimize op-1 db) (-optimize op-2 db) (-optimize op-3 db))))

(defn optimize-helper-1
  [constructor db op-1 op-2 arg]
  (constructor (-optimize op-1 db) (-optimize op-2 db) arg))

(defn optimize-helper-2
  [constructor db op-1 op-2 arg-1 arg-2]
  (constructor (-optimize op-1 db) (-optimize op-2 db) arg-1 arg-2))

(extend-protocol Expression
  nil
  (-static [_]
    true)
  (-attach-cache [expr _]
    [(fn [] [expr])])
  (-patient-count [_]
    nil)
  (-resolve-refs [expr _]
    expr)
  (-resolve-params [expr _]
    expr)
  (-optimize [expr _]
    expr)
  (-eval [expr _ _ _]
    expr)
  (-form [_]
    'nil)

  Object
  (-static [_]
    true)
  (-attach-cache [expr _]
    [(fn [] [expr])])
  (-patient-count [_]
    nil)
  (-resolve-refs [expr _]
    expr)
  (-resolve-params [expr _]
    expr)
  (-optimize [expr _]
    expr)
  (-eval [expr _ _ _]
    expr)
  (-form [expr]
    expr)

  IReduceInit
  (-static [_]
    true)
  (-attach-cache [expr _]
    [(fn [] [expr])])
  (-patient-count [_]
    nil)
  (-resolve-refs [expr _]
    expr)
  (-resolve-params [expr _]
    expr)
  (-optimize [expr _]
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
    (keyword "elm.compiler.type" (elm-util/pascal->kebab type))))

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
