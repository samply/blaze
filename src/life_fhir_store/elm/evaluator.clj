(ns life-fhir-store.elm.evaluator
  "Evaluates compiled expressions.

  Provides the `evaluate` function and a `evaluation-seconds` histogram."
  (:require
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [datomic-spec.core :as ds]
    [life-fhir-store.elm.compiler :as compiler]
    [manifold.deferred :as md]
    [prometheus.alpha :as prom :refer [defhistogram]]
    [taoensso.timbre :as log])
  (:import
    [java.time OffsetDateTime]))


(defhistogram evaluation-seconds
  "Expression evaluation times in seconds."
  [0.0000001 0.0000002 0.0000005
   0.000001 0.000002 0.000005
   0.00001 0.00002 0.00005
   0.0001 0.0002 0.0005
   0.001 0.002 0.005
   0.01 0.02 0.05
   0.1 0.2 0.5
   1 2 5
   10 20 50]
  "name")


(defn- evaluate-expression-async
  "Evaluates `expression` with `name` in `context` asynchronously."
  [name expression context]
  (md/future
    (log/debug "Evaluate expression:" name)
    (with-open [_ (prom/timer evaluation-seconds name)]
      (compiler/-eval expression context nil))))


(defn- evaluate-expression
  "Evaluates `expression` with `name` in `db` using intermediate results
  according to `deps`.

  Just evaluates the expression if there are no dependencies.

  Waits for deferred intermediate results the expression depends on before it
  starts evaluation with realized intermediate results bound to library context."
  [name expression db now deps deferred-intermediate-results]
  (if (empty? deps)
    (evaluate-expression-async name expression {:db db :now now})
    (md/chain'
      ;; Map over deps with intermediate-results to get a list of needed
      ;; intermediate results. Note: we give up the keys here so that the
      ;; position in the list is important
      (apply md/zip' (map deferred-intermediate-results deps))
      (fn [intermediate-results]
        (evaluate-expression-async
          name expression
          ;; We get back the realized intermediate results as list and we join
          ;; it again with the deps in order to obtain a map from expression
          ;; name to result again.
          {:db db :now now :library-context (zipmap deps intermediate-results)})))))


(defn- results
  "Creates a map from expression name to deferred result."
  [db now expression-defs]
  (reduce
    (fn [deferred-intermediate-results
         {::anom/keys [category]
          :keys [name]
          eval-context :context
          :life/keys [expression]
          {:life/keys [deps]} :expression
          :as expression-def}]
      (if category
        (reduced expression-def)
        (if (= "Patient" eval-context)
          ;;TODO
          deferred-intermediate-results
          (assoc deferred-intermediate-results
            name
            (evaluate-expression
              name expression db now (vec deps) deferred-intermediate-results)))))
    {}
    expression-defs))


(defn- types
  "Returns a map of expression-def name to result-type."
  [expression-defs]
  (reduce
    (fn [types
         {::anom/keys [category]
          :keys [name]
          {:keys [resultTypeName resultTypeSpecifier]} :expression}]
      (if category
        types
        (cond
          resultTypeName
          (assoc types name {:type "NamedTypeSpecifier" :name resultTypeName})
          resultTypeSpecifier
          (assoc types name resultTypeSpecifier)
          :else
          types)))
    {}
    expression-defs))


(defn- locators
  "Returns a map of expression-def name to locator."
  [expression-defs]
  (reduce
    (fn [locators
         {::anom/keys [category]
          :keys [name locator]}]
      (if category
        locators
        (assoc locators name locator)))
    {}
    expression-defs))


(defn- assoc-types-and-locators [results types locators]
  (into
    {}
    (map
      (fn [[name result]]
        [name {:result result
               :type (get types name)
               :locator (get locators name)}]))
    results))


(s/fdef evaluate
  :args (s/cat :db ::ds/db :now #(instance? OffsetDateTime %)
               :expression-defs (s/coll-of :life/compiled-expression-def))
  :ret (s/or :results md/deferred? :anomaly ::anom/anomaly))

(defn evaluate [db now expression-defs]
  (let [results (results db now expression-defs)]
    (if (::anom/category results)
      results
      (let [keys (vec (keys results))]
        (md/chain'
          (apply md/zip' (map results keys))
          (fn [results]
            (assoc-types-and-locators (zipmap keys results)
                                      (types expression-defs)
                                      (locators expression-defs))))))))
