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


(defn- expression-result-combine-op [{:keys [report-type]}]
  (case report-type
    "population" +
    "subject-list" into))


(defn- expression-result-reduce-op [{:keys [report-type]}]
  (case report-type
    "population" (fn [result _] (inc result))
    "subject-list" (fn [result {:keys [id]}] (conj result id))))


(defn- expression-combine-op [context]
  (-> (expression-result-combine-op context)
      (wrap-anomaly)
      (wrap-batch-db context)))


(defn- evaluate-expression*
  "Evaluates the expression with `name` over `subjects` parallel.

  Subjects have to be a vector in order ro ensure parallel execution."
  [context name subjects]
  (let [reduce-result-op (expression-result-reduce-op context)]
    (r/fold
      eval-sequential-chunk-size
      (expression-combine-op context)
      (fn [context subject]
        (let [res (evaluate-expression-1 context subject name)]
          (cond
            (::anom/category res)
            (reduced (assoc context ::result res))

            res
            (update context ::result reduce-result-op subject)

            :else
            context)))
      subjects)))


(defn- unwrap-library-context
  {:arglists '([context])}
  [{{expression-defs :life/compiled-expression-defs} :library :as context}]
  (assoc context :library-context expression-defs))


(defn evaluate-expression
  "Evaluates the expression with `name` according to `context`.

  Depending on :report-type of `context`, returns either the number of or a
  vector of the actual subject id's of expressions evaluated to true.

  Returns an anomaly in case of errors."
  {:arglists '([context name])}
  [{:keys [db subject-type] :as context} name]
  (let [context (unwrap-library-context context)]
    (transduce
      (comp
        (partition-all eval-parallel-chunk-size)
        (map #(evaluate-expression* context name %)))
      (expression-combine-op context)
      (d/list-resources db subject-type))))


(defn- incorrect-stratum [resource expression-name]
  {::anom/category ::anom/incorrect
   ::anom/message
   (format "CQL expression `%s` returned more than one value for resource `%s`."
           expression-name (str (:resourceType resource) "/" (:id resource)))})


(defn- stratum-result-combine-op [{:keys [report-type]}]
  (case report-type
    "population" (partial merge-with +)
    "subject-list" (partial merge-with into)))


(defn- stratum-result-reduce-op [{:keys [report-type]}]
  (case report-type
    "population"
    (fn [result stratum _] (update result stratum (fnil inc 0)))
    "subject-list"
    (fn [result stratum {:keys [id]}] (update result stratum (fnil conj []) id))))


(defn- stratum-combine-op [context]
  (-> (stratum-result-combine-op context)
      (wrap-anomaly)
      (wrap-batch-db context)))


(defn- evaluate-stratum-expression [context subject name]
  (let [result (evaluate-expression-1 context subject name)]
    (if (sequential? result)
      (incorrect-stratum subject name)
      result)))


(defn calc-strata*
  [context population-expression-name stratum-expression-name subjects]
  (let [stratum-result-reduce-op (stratum-result-reduce-op context)]
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
            (let [stratum (evaluate-stratum-expression
                            context subject stratum-expression-name)]
              (if (::anom/category stratum)
                (reduced (assoc context ::result stratum))
                (update context ::result stratum-result-reduce-op stratum subject)))

            :else
            context)))
      subjects)))


(defn calc-strata
  "Returns a map of stratum to count or an anomaly."
  {:arglists '([[context population-expression-name stratum-expression-name]])}
  [{:keys [db subject-type] :as context} population-expression-name
   stratum-expression-name]
  (let [context (unwrap-library-context context)]
    (transduce
      (comp
        (partition-all eval-parallel-chunk-size)
        (map (partial calc-strata* context population-expression-name
                      stratum-expression-name)))
      (stratum-combine-op context)
      (d/list-resources db subject-type))))


(defn- anom-conj
  ([] [])
  ([r] r)
  ([r x] (if (::anom/category x) (reduced x) (conj r x))))


(defn- evaluate-mult-component-stratum-expression [context subject names]
  (transduce
    (map #(evaluate-stratum-expression context subject %))
    anom-conj
    names))


(defn calc-mult-component-strata*
  [context population-expression-name stratum-expression-names subjects]
  (let [stratum-result-reduce-op (stratum-result-reduce-op context)]
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
            (let [stratum (evaluate-mult-component-stratum-expression
                            context subject stratum-expression-names)]

              (if (::anom/category stratum)
                (reduced (assoc context ::result stratum))
                (update context ::result stratum-result-reduce-op stratum subject)))

            :else
            context)))
      subjects)))


(defn calc-mult-component-strata
  "Returns a map of stratum to count or an anomaly."
  {:arglists '([[context population-expression-name expression-names]])}
  [{:keys [db subject-type] :as context} population-expression-name
   stratum-expression-names]
  (let [context (unwrap-library-context context)]
    (transduce
      (comp
        (partition-all eval-parallel-chunk-size)
        (map (partial calc-mult-component-strata* context
                      population-expression-name stratum-expression-names)))
      (stratum-combine-op context)
      (d/list-resources db subject-type))))
