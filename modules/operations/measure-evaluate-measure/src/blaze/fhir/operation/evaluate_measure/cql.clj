(ns blaze.fhir.operation.evaluate-measure.cql
  (:require
    [blaze.anomaly :refer [when-ok]]
    [blaze.db.api :as d]
    [blaze.elm.expression :as expr]
    [clojure.core.reducers :as r]
    [cognitect.anomalies :as anom]))


(set! *warn-on-reflection* true)


(def eval-parallel-chunk-size
  "Size of chunks of resources to evaluate parallel. Each chunk consists of a
  vector of resources which are evaluated parallel using r/fold.

  The chunk size should be limited because all resources of a chunk have to fit
  in memory."
  100000)


(def eval-sequential-chunk-size
  "Size of chunks of resources to evaluate sequential. This size is used by
  r/fold as a cut off point for the fork-join algorithm.

  Each chunk of those resources if evaluated sequential and the results of
  multiple of those parallel evaluations are combined afterwards."
  512)


(defn- evaluate-expression-1
  [{:keys [library-context] :as context} subject expression-name]
  (try
    (expr/eval (get library-context expression-name) context subject nil)
    (catch Exception e
      {::anom/category ::anom/fault
       ::anom/message (ex-message e)
       :fhir/issue "exception"
       :expression-name expression-name})))


(defn- combine
  ([] 0)
  ([x] x)
  ([a b]
   (cond
     (::anom/category a) a
     (::anom/category b) b
     :else (+ a b))))


(defn- evaluate-expression*
  "Evaluates the expression with `name` over `subjects` parallel.

  Subjects have to be a vector in order ro ensure parallel execution."
  [context name subjects]
  (r/fold
    eval-sequential-chunk-size
    combine
    (fn [count subject]
      (let [res (evaluate-expression-1 context subject name)]
        (cond
          (::anom/category res)
          (reduced res)

          res
          (inc count)

          :else
          count)))
    subjects))


(defn- create-context
  {:arglists '([db now library])}
  [db now {expression-defs :life/compiled-expression-defs}]
  {:db db :now now :library-context expression-defs})


(defn evaluate-expression
  "Evaluates the expression with `name` from `library` over all resources of
  `subject-type` using `db` and `now`.

  Returns the number of expressions evaluated to true or an anomaly."
  [db now library subject-type name]
  (let [context (create-context db now library)]
    (transduce
      (comp
        (partition-all eval-parallel-chunk-size)
        (map (partial evaluate-expression* context name)))
      combine
      (d/list-resources db subject-type))))


(defn- incorrect-stratum [resource expression-name]
  {::anom/category ::anom/incorrect
   ::anom/message
   (format "CQL expression `%s` returned more than one value for resource `%s`."
           expression-name (str (:resourceType resource) "/" (:id resource)))})


(defn- stratum-combine
  ([] {})
  ([x] x)
  ([a b]
   (cond
     (::anom/category a) a
     (::anom/category b) b
     :else (merge-with + a b))))


(defn calc-stratums*
  [context population-expression-name stratum-expression-name subjects]
  (r/fold
    eval-sequential-chunk-size
    stratum-combine
    (fn [stratums subject]
      (let [res (evaluate-expression-1 context subject
                                       population-expression-name)]
        (cond
          (::anom/category res)
          (reduced res)

          res
          (let [stratum (evaluate-expression-1 context subject
                                               stratum-expression-name)]
            (cond
              (::anom/category stratum)
              (reduced stratum)

              (sequential? stratum)
              (reduced (incorrect-stratum subject stratum-expression-name))

              :else
              (update stratums stratum (fnil inc 0))))

          :else
          stratums)))
    subjects))


(defn calc-stratums
  "Returns a map of stratum to count or an anomaly."
  [db now library subject-type population-expression-name
   stratum-expression-name]
  (let [context (create-context db now library)]
    (transduce
      (comp
        (partition-all eval-parallel-chunk-size)
        (map (partial calc-stratums* context population-expression-name
                      stratum-expression-name)))
      stratum-combine
      (d/list-resources db subject-type))))


(defn calc-mult-component-stratums*
  [context population-expression-name expression-names subjects]
  (r/fold
    eval-sequential-chunk-size
    stratum-combine
    (fn [stratums subject]
      (when-ok [res (evaluate-expression-1 context subject
                                           population-expression-name)]
        (if res
          (let [stratum-vector
                (reduce
                  (fn [stratum-vector expression-name]
                    (let [stratum (evaluate-expression-1
                                    context subject expression-name)]
                      (cond
                        (::anom/category stratum)
                        (reduced stratum)

                        (sequential? stratum)
                        (reduced (incorrect-stratum subject expression-name))

                        :else
                        (conj stratum-vector stratum))))
                  []
                  expression-names)]
            (if (::anom/category stratum-vector)
              (reduced stratum-vector)
              (update stratums stratum-vector (fnil inc 0))))
          stratums)))
    subjects))


(defn calc-mult-component-stratums
  "Returns a map of stratum to count."
  [db now library subject-type population-expression-name expression-names]
  (let [context (create-context db now library)]
    (transduce
      (comp
        (partition-all eval-parallel-chunk-size)
        (map (partial calc-mult-component-stratums* context
                      population-expression-name expression-names)))
      stratum-combine
      (d/list-resources db subject-type))))
