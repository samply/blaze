(ns blaze.fhir.operation.evaluate-measure.cql
  (:require
    [blaze.datomic.util :as datomic-util]
    [blaze.elm.compiler.protocols :refer [-eval]]
    [clojure.core.reducers :as r]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [datomic-spec.core :as ds])
  (:import
    [java.time OffsetDateTime]))


(defn- evaluate-expression-1
  [context resource {expression-defs :life/compiled-expression-defs}
   expression-name]
  (-> (reduce
        (fn [context {:keys [name] :life/keys [expression]}]
          (let [res (-eval expression context resource nil)]
            (update context :library-context assoc name res)))
        context
        expression-defs)
      :library-context
      (get expression-name)))


(def ^:private resource-cache (volatile! {}))


(defn clear-resource-cache! []
  (vreset! resource-cache {}))


(defn- list-resources [db resource-type]
  (let [key [resource-type (datomic-util/type-version db resource-type)]]
    (if-let [resources (get @resource-cache key)]
      resources
      (let [resources (into [] (datomic-util/list-resources db resource-type))]
        (vswap! resource-cache assoc key resources)
        resources))))


(s/fdef evaluate-expression
  :args (s/cat :db ::ds/db :now #(instance? OffsetDateTime %)
               :library :life/compiled-library :subject string?
               :expression-name string?))

(defn evaluate-expression
  [db now library subject expression-name]
  (let [context {:db db :now now}]
    (r/fold
      +
      (fn [count resource]
        (if (evaluate-expression-1 context resource library expression-name)
          (inc count)
          count))
      (list-resources db subject))))


(defn- incorrect-stratum [resource expression-name]
  {::anom/category ::anom/incorrect
   ::anom/message
   (format "CQL expression `%s` returned more than one value for resource `%s`."
           expression-name (str/join "/" (datomic-util/literal-reference resource)))})


(defn- combine
  ([] {})
  ([a b]
   (cond
     (::anom/category a) a
     (::anom/category b) b
     :else (merge-with + a b))))


(s/fdef calc-stratums
  :args (s/cat :db ::ds/db :now #(instance? OffsetDateTime %)
               :library :life/compiled-library :subject string?
               :population-expression-name string?
               :expression-name string?))

(defn calc-stratums
  "Returns a map of stratum to count."
  [db now library subject population-expression-name expression-name]
  (let [context {:db db :now now}]
    (r/fold
      combine
      (fn [stratums resource]
        (if (evaluate-expression-1 context resource library
                                   population-expression-name)
          (let [stratum (evaluate-expression-1 context resource library
                                               expression-name)]
            (if (sequential? stratum)
              (reduced (incorrect-stratum resource expression-name))
              (update stratums stratum (fnil inc 0))))
          stratums))
      (list-resources db subject))))


(s/fdef calc-mult-component-stratums
  :args (s/cat :db ::ds/db :now #(instance? OffsetDateTime %)
               :library :life/compiled-library :subject string?
               :population-expression-name string?
               :expression-names (s/coll-of string?)))

(defn calc-mult-component-stratums
  "Returns a map of stratum to count."
  [db now library subject population-expression-name expression-names]
  (let [context {:db db :now now}]
    (r/fold
      combine
      (fn [stratums resource]
        (if (evaluate-expression-1 context resource library
                                   population-expression-name)
          (let [stratum-vector
                (reduce
                  (fn [stratum-vector expression-name]
                    (let [stratum (evaluate-expression-1
                                    context resource library expression-name)]
                      (if (sequential? stratum)
                        (reduced (incorrect-stratum resource expression-name))
                        (conj stratum-vector stratum))))
                  []
                  expression-names)]
            (if (::anom/category stratum-vector)
              (reduced stratum-vector)
              (update stratums stratum-vector (fnil inc 0))))
          stratums))
      (list-resources db subject))))
