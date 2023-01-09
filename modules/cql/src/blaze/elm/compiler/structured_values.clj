(ns blaze.elm.compiler.structured-values
  "2. Structured Values

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.anomaly :as ba :refer [throw-anom]]
    [blaze.coll.core :as coll]
    [blaze.db.api :as d]
    [blaze.elm.code :as code]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.protocols :as p]
    [blaze.fhir.spec :as fhir-spec]
    [clojure.string :as str])
  (:import
    [clojure.lang ILookup IReduceInit]))


(set! *warn-on-reflection* true)


;; 2.1. Tuple
(defrecord TupleExpression [elements]
  core/Expression
  (-eval [_ context resource scope]
    (reduce-kv
      (fn [r key value]
        (assoc r key (core/-eval value context resource scope)))
      {}
      elements)))


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


(defmethod core/compile* :elm.compiler.type/tuple
  [context {elements :element}]
  (let [elements (compile-elements context elements)]
    (if (every? core/static? (vals elements))
      elements
      (->TupleExpression elements))))


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
(defn- get-property [db key x]
  (if (d/resource-handle? x)
    (get @(d/pull-content db x) key)
    (p/get x key)))


(defrecord SourcePropertyExpression [source key]
  core/Expression
  (-eval [_ {:keys [db] :as context} resource scope]
    (get-property db key (core/-eval source context resource scope)))
  (-form [_]
    `(~key ~(core/-form source))))


(defrecord SingleScopePropertyExpression [key]
  core/Expression
  (-eval [_ {:keys [db]} _ value]
    (get-property db key value))
  (-form [_]
    `(~key ~'default)))


(defrecord ScopePropertyExpression [scope-key key]
  core/Expression
  (-eval [_ {:keys [db]} _ scope]
    (get-property db key (get scope scope-key)))
  (-form [_]
    `(~key ~(symbol (name scope-key)))))


(defn- path->key [path]
  (let [[first-part & more-parts] (str/split path #"\." 2)]
    (when (and more-parts (not= ["value"] more-parts))
      (throw-anom (ba/unsupported (format "Unsupported path `%s`with more than one part." path))))
    (keyword first-part)))


(defmethod core/compile* :elm.compiler.type/property
  [context {:keys [source scope path]}]
  (let [key (path->key path)]
    (cond
      source
      (->SourcePropertyExpression (core/compile* context source) key)

      scope
      (->ScopePropertyExpression scope key))))
