(ns blaze.fhir.operation.evaluate-measure.cql
  (:require
    [blaze.anomaly :refer [when-ok]]
    [blaze.db.api :as d]
    [blaze.elm.compiler.protocols :refer [-eval]]
    [clojure.core.reducers :as r]
    [cognitect.anomalies :as anom]))


(set! *warn-on-reflection* true)


(defn- evaluate-expression-1
  [context resource {expression-defs :life/compiled-expression-defs}
   expression-name]
  (try
    (-eval
      (get expression-defs expression-name)
      (assoc context :library-context expression-defs)
      resource
      nil)
    (catch Exception e
      {::anom/category ::anom/fault
       ::anom/message (ex-message e)
       :fhir/issue "exception"
       :expression-name expression-name})))


(defn- list-resources [db resource-type]
  (into [] (d/list-resources db resource-type)))


(defn- combine
  ([] 0)
  ([a b]
   (cond
     (::anom/category a) a
     (::anom/category b) b
     :else (+ a b))))


(defn evaluate-expression
  "Evaluates the expression with `name` from `library` over all resources of
  `subject-type` using `db` and `now`.

  Returns the number of expressions evaluated to true or an anomaly."
  [db now library subject-type name]
  (let [context {:db db :now now}]
    (r/fold
      combine
      (fn [count resource]
        (when-ok [res (evaluate-expression-1 context resource library name)]
          (if res
            (inc count)
            count)))
      (list-resources db subject-type))))


(defn- incorrect-stratum [resource expression-name]
  {::anom/category ::anom/incorrect
   ::anom/message
   (format "CQL expression `%s` returned more than one value for resource `%s`."
           expression-name (str (:resourceType resource) "/" (:id resource)))})


(defn- stratum-combine
  ([] {})
  ([a b]
   (cond
     (::anom/category a) a
     (::anom/category b) b
     :else (merge-with + a b))))


(defn calc-stratums
  "Returns a map of stratum to count or an anomaly."
  [db now library subject-type population-expression-name stratum-expression-name]
  (let [context {:db db :now now}]
    (r/fold
      stratum-combine
      (fn [stratums resource]
        (let [res (evaluate-expression-1 context resource library
                                         population-expression-name)]
          (cond
            (::anom/category res)
            (reduced res)

            res
            (let [stratum (evaluate-expression-1 context resource library
                                                 stratum-expression-name)]
              (cond
                (::anom/category stratum)
                (reduced stratum)

                (sequential? stratum)
                (reduced (incorrect-stratum resource stratum-expression-name))

                :else
                (update stratums stratum (fnil inc 0))))

            :else
            stratums)))
      (list-resources db subject-type))))


(defn calc-mult-component-stratums
  "Returns a map of stratum to count."
  [db now library subject-type population-expression-name expression-names]
  (let [context {:db db :now now}]
    (r/fold
      stratum-combine
      (fn [stratums resource]
        (when-ok [res (evaluate-expression-1 context resource library
                                             population-expression-name)]
          (if res
            (let [stratum-vector
                  (reduce
                    (fn [stratum-vector expression-name]
                      (let [stratum (evaluate-expression-1
                                      context resource library expression-name)]
                        (cond
                          (::anom/category stratum)
                          (reduced stratum)

                          (sequential? stratum)
                          (reduced (incorrect-stratum resource expression-name))

                          :else
                          (conj stratum-vector stratum))))
                    []
                    expression-names)]
              (if (::anom/category stratum-vector)
                (reduced stratum-vector)
                (update stratums stratum-vector (fnil inc 0))))
            stratums)))
      (list-resources db subject-type))))
