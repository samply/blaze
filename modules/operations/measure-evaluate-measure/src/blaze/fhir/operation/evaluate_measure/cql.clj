(ns blaze.fhir.operation.evaluate-measure.cql
  (:require
    [blaze.db.api :as d]
    [blaze.elm.expression :as expr]
    [clojure.core.reducers :as r]
    [cognitect.anomalies :as anom]
    [taoensso.timbre :as log])
  (:import
    [java.io Closeable]))


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
      (log/error (log/stacktrace e))
      {::anom/category ::anom/fault
       ::anom/message (ex-message e)
       :fhir/issue "exception"
       :expression-name expression-name})))


(defn- close-batch-db! [{:keys [db]}]
  (.close ^Closeable db))


(defn- wrap-batch-db
  "Wraps `combine-op`, so that when used with `r/fold`, a new batch database is
  created in every single-threaded reduce step.

  When called with no argument, it returns `context` where :db is replaced with
  a new batch database and ::result will be initialized to `(combine-op)`.

  When called with one context, it closes the batch database and returns the
  result of calling `combine-op` with the value at ::result.

  When called with two contexts, it closes the batch database from one context
  and returns the other context with the value at ::result combined with
  `combine-op`."
  [combine-op context]
  (fn batch-db-combine-op
    ([]
     (-> (update context :db d/new-batch-db)
         (assoc ::result (combine-op))))
    ([context]
     (close-batch-db! context)
     (combine-op (::result context)))
    ([context-a context-b]
     (close-batch-db! context-b)
     (update context-a ::result (partial combine-op (::result context-b))))))


(defn- wrap-anomaly
  [combine-op]
  (fn anomaly-combine-op
    ([] (combine-op))
    ([r] r)
    ([a b]
     (cond
       (::anom/category a) a
       (::anom/category b) b
       :else (combine-op a b)))))


(defn- evaluate-expression*
  "Evaluates the expression with `name` over `subjects` parallel.

  Subjects have to be a vector in order ro ensure parallel execution."
  [context name subjects]
  (r/fold
    eval-sequential-chunk-size
    (-> + (wrap-anomaly) (wrap-batch-db context))
    (fn [context subject]
      (let [res (evaluate-expression-1 context subject name)]
        (cond
          (::anom/category res)
          (reduced (assoc context ::result res))

          res
          (update context ::result inc)

          :else
          context)))
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
        (map #(evaluate-expression* context name %)))
      (-> + (wrap-anomaly) (wrap-batch-db context))
      (d/list-resources db subject-type))))


(defn- incorrect-stratum [resource expression-name]
  {::anom/category ::anom/incorrect
   ::anom/message
   (format "CQL expression `%s` returned more than one value for resource `%s`."
           expression-name (str (:resourceType resource) "/" (:id resource)))})


(defn- stratum-combine-op [context]
  (-> (partial merge-with +) wrap-anomaly (wrap-batch-db context)))


(defn calc-stratums*
  [context population-expression-name stratum-expression-name subjects]
  (r/fold
    eval-sequential-chunk-size
    (stratum-combine-op context)
    (fn [context subject]
      (let [res (evaluate-expression-1 context subject
                                       population-expression-name)]
        (cond
          (::anom/category res)
          (reduced (assoc context ::result res))

          res
          (let [stratum (evaluate-expression-1 context subject
                                               stratum-expression-name)]
            (cond
              (::anom/category stratum)
              (reduced (assoc context ::result stratum))

              (sequential? stratum)
              (reduced (assoc context ::result (incorrect-stratum subject stratum-expression-name)))

              :else
              (update-in context [::result stratum] (fnil inc 0))))

          :else
          context)))
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
      (stratum-combine-op context)
      (d/list-resources db subject-type))))


(defn calc-mult-component-stratums*
  [context population-expression-name expression-names subjects]
  (r/fold
    eval-sequential-chunk-size
    (stratum-combine-op context)
    (fn [context subject]
      (let [res (evaluate-expression-1 context subject
                                       population-expression-name)]
        (cond
          (::anom/category res)
          (reduced (assoc context ::result res))

          res
          (let [stratum-vector
                (reduce
                  (fn [stratum-vector expression-name]
                    (let [stratum (evaluate-expression-1
                                    context subject expression-name)]
                      (cond
                        (::anom/category stratum)
                        (reduced (assoc context ::result stratum))

                        (sequential? stratum)
                        (reduced (assoc context ::result (incorrect-stratum subject expression-name)))

                        :else
                        (conj stratum-vector stratum))))
                  []
                  expression-names)]

            (if (::anom/category stratum-vector)
              (reduced (assoc context ::result stratum-vector))
              (update-in context [::result stratum-vector] (fnil inc 0))))

          :else
          context)))
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
      (stratum-combine-op context)
      (d/list-resources db subject-type))))
