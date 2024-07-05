(ns blaze.elm.compiler.structured-values
  "2. Structured Values

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.anomaly :as ba :refer [throw-anom]]
   [blaze.coll.core :as coll]
   [blaze.elm.code :as code]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.macros :refer [reify-expr]]
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
  (reify-expr core/Expression
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
          (code/code system version code))))))

;; 2.3. Property
(defrecord SourcePropertyExpression [source key]
  core/Expression
  (-static [_]
    false)
  (-attach-cache [expr _]
    [(fn [] [expr])])
  (-patient-count [_]
    nil)
  (-resolve-refs [_ expression-defs]
    (->SourcePropertyExpression (core/-resolve-refs source expression-defs) key))
  (-resolve-params [_ parameters]
    (->SourcePropertyExpression (core/-resolve-params source parameters) key))
  (-eval [_ context resource scope]
    (p/get (core/-eval source context resource scope) key))
  (-form [_]
    `(~key ~(core/-form source))))

(defn- source-property-value-expr [source key]
  (reify-expr core/Expression
    (-resolve-refs [_ expression-defs]
      (source-property-value-expr (core/-resolve-refs source expression-defs) key))
    (-resolve-params [_ parameters]
      (source-property-value-expr (core/-resolve-params source parameters) key))
    (-eval [_ context resource scope]
      (type/value (p/get (core/-eval source context resource scope) key)))
    (-form [_]
      `(:value (~key ~(core/-form source))))))

(defn- scope-property-expr [scope-key key]
  (reify-expr core/Expression
    (-eval [_ _ _ scope]
      (p/get (get scope scope-key) key))
    (-form [_]
      `(~key ~(symbol (name scope-key))))))

(defn- scope-property-value-expr [scope-key key]
  (reify-expr core/Expression
    (-eval [_ _ _ scope]
      (type/value (p/get (get scope scope-key) key)))
    (-form [_]
      `(:value (~key ~(symbol (name scope-key)))))))

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
        (source-property-value-expr (core/compile* context source) key)
        (->SourcePropertyExpression (core/compile* context source) key))

      scope
      (if value?
        (scope-property-value-expr scope key)
        (scope-property-expr scope key)))))
