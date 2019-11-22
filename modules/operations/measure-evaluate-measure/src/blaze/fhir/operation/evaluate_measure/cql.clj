(ns blaze.fhir.operation.evaluate-measure.cql
  (:require
    [blaze.datomic.util :as datomic-util]
    [blaze.elm.compiler.protocols :refer [-eval]]
    [clojure.core.reducers :as r]
    [clojure.spec.alpha :as s]
    [datomic-spec.core :as ds])
  (:import
    [java.time OffsetDateTime]))


(defn- evaluate-expression-1
  [context resource expression-defs]
  (reduce
    (fn [context {:keys [name] :life/keys [expression]}]
      (let [res (-eval expression context resource nil)]
        (update context :library-context assoc name res)))
    context
    expression-defs))


(def ^:private resource-cache (volatile! {}))


(defn clear-resource-cache! []
  (vreset! resource-cache {}))


(defn- list-resources [db resource-type]
  (let [key [resource-type (datomic-util/type-version db resource-type)]]
    (if-let [resources (get @resource-cache key)]
      resources
      (let [resources (datomic-util/list-resources db resource-type)]
        (vswap! resource-cache assoc key resources)
        resources))))


(s/fdef evaluate-expression
  :args (s/cat :db ::ds/db :now #(instance? OffsetDateTime %)
               :library :life/compiled-library :subject string?
               :expression-name string?))

(defn evaluate-expression
  {:arglists '([db now library subject expression-name])}
  [db now {expression-defs :life/compiled-expression-defs} subject
   expression-name]
  (let [context {:db db :now now}]
    (r/fold
      +
      (fn [count resource]
        (if (-> (evaluate-expression-1 context resource expression-defs)
                :library-context
                (get expression-name))
          (inc count)
          count))
      (list-resources db subject))))


(s/fdef calc-stratums
  :args (s/cat :db ::ds/db :now #(instance? OffsetDateTime %)
               :library :life/compiled-library :subject string?
               :population-expression-name string?
               :expression-name string?))

(defn calc-stratums
  "Returns a map of stratum to count."
  [db now {expression-defs :life/compiled-expression-defs} subject
   population-expression-name expression-name]
  (let [context {:db db :now now}]
    (r/fold
      (partial merge-with +)
      (fn [stratums resource]
        (if (-> (evaluate-expression-1
                  context resource expression-defs)
                :library-context
                (get population-expression-name))
          (let [stratum (-> (evaluate-expression-1
                              context resource expression-defs)
                            :library-context
                            (get expression-name))]
            (if (sequential? stratum)
              (reduce #(update %1 %2 (fnil inc 0)) stratums stratum)
              (update stratums stratum (fnil inc 0))))
          stratums))
      (list-resources db subject))))
