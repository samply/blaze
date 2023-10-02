(ns blaze.elm.compiler.structured-values
  "2. Structured Values

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.anomaly :as ba :refer [throw-anom]]
   [blaze.coll.core :as coll]
   [blaze.elm.code :as code]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.protocols :as p]
   [blaze.fhir.spec.type :as type]
   [clojure.string :as str])
  (:import
   [clojure.lang ILookup IReduceInit]))

(set! *warn-on-reflection* true)

;; 2.1. Tuple
(defn- invalid-structured-type-access-msg [key]
  (format "Invalid structured type access with key `%s` on a collection." key))

(defn- invalid-structured-type-access-anom [coll key]
  (ba/fault
   (invalid-structured-type-access-msg key)
   :key key
   :first-elem (coll/first coll)))

(extend-protocol p/StructuredType
  IReduceInit
  (get [coll key]
    (throw-anom (invalid-structured-type-access-anom coll key)))
  ILookup
  (get [m key]
    (.valAt m key)))

(defn- compile-elements [context elements]
  (reduce
   (fn [r {:keys [name value]}]
     (assoc r (keyword name) (core/compile* context value)))
   {}
   elements))

(defn tuple [elements]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (tuple
       (reduce-kv
        (fn [r key value]
          (assoc r key (core/-attach-cache value cache)))
        {}
        elements)))
    (-resolve-refs [_ expression-defs]
      (tuple
       (reduce-kv
        (fn [r key value]
          (assoc r key (core/-resolve-refs value expression-defs)))
        {}
        elements)))
    (-resolve-params [_ parameters]
      (tuple
       (reduce-kv
        (fn [r key value]
          (assoc r key (core/-resolve-params value parameters)))
        {}
        elements)))
    (-eval [_ context resource scope]
      (reduce-kv
       (fn [r key value]
         (assoc r key (core/-eval value context resource scope)))
       {}
       elements))
    (-form [_]
      (reduce-kv
       (fn [r key value]
         (assoc r key (core/-form value)))
       {}
       elements))))

(defmethod core/compile* :elm.compiler.type/tuple
  [context {elements :element}]
  (let [elements (compile-elements context elements)]
    (cond-> elements (some (comp not core/static?) (vals elements)) tuple)))

;; 2.2. Instance
(defmethod core/compile* :elm.compiler.type/instance
  [context {type :classType elements :element}]
  (let [elements (compile-elements context elements)]
    (when (every? core/static? (vals elements))
      (case type
        "{urn:hl7-org:elm-types:r1}Code"
        (let [{:keys [system version code]} elements]
          (code/to-code system version code))))))

;; 2.3. Property
(defrecord SourcePropertyExpression [source key]
  core/Expression
  (-static [_]
    false)
  (-attach-cache [_ cache]
    (->SourcePropertyExpression (core/-attach-cache source cache) key))
  (-resolve-refs [_ expression-defs]
    (->SourcePropertyExpression (core/-resolve-refs source expression-defs) key))
  (-resolve-params [_ parameters]
    (->SourcePropertyExpression (core/-resolve-params source parameters) key))
  (-eval [_ context resource scope]
    (p/get (core/-eval source context resource scope) key))
  (-form [_]
    `(~key ~(core/-form source))))

(defrecord SourcePropertyValueExpression [source key]
  core/Expression
  (-static [_]
    false)
  (-attach-cache [_ cache]
    (->SourcePropertyValueExpression (core/-attach-cache source cache) key))
  (-resolve-refs [_ expression-defs]
    (->SourcePropertyValueExpression (core/-resolve-refs source expression-defs) key))
  (-resolve-params [_ parameters]
    (->SourcePropertyValueExpression (core/-resolve-params source parameters) key))
  (-eval [_ context resource scope]
    (type/value (p/get (core/-eval source context resource scope) key)))
  (-form [_]
    `(:value (~key ~(core/-form source)))))

(defrecord SingleScopePropertyExpression [key]
  core/Expression
  (-static [_]
    false)
  (-attach-cache [expr _]
    expr)
  (-resolve-refs [expr _]
    expr)
  (-resolve-params [expr _]
    expr)
  (-eval [_ _ _ value]
    (p/get value key))
  (-form [_]
    `(~key ~'default)))

(defrecord ScopePropertyExpression [scope-key key]
  core/Expression
  (-static [_]
    false)
  (-attach-cache [expr _]
    expr)
  (-resolve-refs [expr _]
    expr)
  (-resolve-params [expr _]
    expr)
  (-eval [_ _ _ scope]
    (p/get (get scope scope-key) key))
  (-form [_]
    `(~key ~(symbol (name scope-key)))))

(defrecord ScopePropertyValueExpression [scope-key key]
  core/Expression
  (-static [_]
    false)
  (-attach-cache [expr _]
    expr)
  (-resolve-refs [expr _]
    expr)
  (-resolve-params [expr _]
    expr)
  (-eval [_ _ _ scope]
    (type/value (p/get (get scope scope-key) key)))
  (-form [_]
    `(:value (~key ~(symbol (name scope-key))))))

(defn- path->key [path]
  (let [[first-part more] (str/split path #"\." 2)]
    (if more
      (if (= "value" more)
        [(keyword first-part) true]
        (throw-anom (ba/unsupported (format "Unsupported path `%s`with more than one part." path))))
      [(keyword first-part)])))

(defmethod core/compile* :elm.compiler.type/property
  [context {:keys [source scope path]}]
  (let [[key value?] (path->key path)]
    (cond
      source
      (if value?
        (->SourcePropertyValueExpression (core/compile* context source) key)
        (->SourcePropertyExpression (core/compile* context source) key))

      scope
      (if value?
        (->ScopePropertyValueExpression scope key)
        (->ScopePropertyExpression scope key)))))
