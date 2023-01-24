(ns blaze.fhir.operation.evaluate-measure.cql
  (:require
    [blaze.anomaly :as ba :refer [if-ok when-ok]]
    [blaze.db.api :as d]
    [blaze.elm.expression :as expr]
    [blaze.elm.util :as elm-util]
    [blaze.fhir.spec :as fhir-spec]
    [clojure.core.reducers :as r]
    [taoensso.timbre :as log])
  (:import
    [java.lang AutoCloseable]))


(set! *warn-on-reflection* true)


(def eval-parallel-chunk-size
  "Size of chunks of resources to evaluate parallel. Each chunk consists of a
  vector of resources which are evaluated parallel using r/fold.

  The chunk size should be limited because all resources of a chunk have to fit
  in memory."
  100000)


(def eval-sequential-chunk-size
  "Size of chunks of resources to evaluate sequential. This size is used by
  r/fold as a cut-off point for the fork-join algorithm.

  Each chunk of those resources if evaluated sequential and the results of
  multiple of those parallel evaluations are combined afterwards."
  512)


(defn- evaluate-expression-1-error-msg [expression-name e]
  (format "Error while evaluating the expression `%s`: %s" expression-name
          (ex-message e)))


(defn- evaluate-expression-1 [context subject-handle name expression]
  (try
    (expr/eval context expression subject-handle)
    (catch Exception e
      (let [ex-data (ex-data e)]
        ;; only log if the exception hasn't ex-data because exception with
        ;; ex-data are controlled by us and so are not unexpected
        (when-not ex-data
          (log/error (evaluate-expression-1-error-msg name e))
          (log/error e))
        (-> (ba/fault
              (evaluate-expression-1-error-msg name e)
              :fhir/issue "exception"
              :expression-name name)
            (merge ex-data))))))


(defn- close-batch-db! [{:keys [db]}]
  (.close ^AutoCloseable db))


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
     (-> (update context :db d/new-batch-db) (assoc ::result (combine-op))))
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
    ([r] (if (ba/anomaly? r) r (combine-op r)))
    ([a b]
     (cond
       (ba/anomaly? a) a
       (ba/anomaly? b) b
       :else (combine-op a b)))))


(defn- expression-combine-op [context]
  (-> (fn
        ([] (transient []))
        ([x] (persistent! x))
        ([a b] (reduce conj! a (persistent! b))))
      (wrap-anomaly)
      (wrap-batch-db context)))


(defn- handle [subject-handle]
  {:population-handle subject-handle :subject-handle subject-handle})


(defn- conj-all! [handles subject-handle population-handles]
  (reduce
    (fn [handles population-handle]
      (conj! handles {:population-handle population-handle
                      :subject-handle subject-handle}))
    handles
    population-handles))


(defn- evaluate-expression**
  "Evaluates the expression within `def` over `subject-handles` parallel.

  Subject handles have to be a vector in order to ensure parallel execution."
  [context {:keys [name expression]} subject-handles population-basis]
  (r/fold
    eval-sequential-chunk-size
    (expression-combine-op context)
    (fn [context subject-handle]
      (if-ok [res (evaluate-expression-1 context subject-handle name expression)]
        (if (identical? :boolean population-basis)
          (cond-> context res (update ::result conj! (handle subject-handle)))
          (update context ::result conj-all! subject-handle res))
        #(reduced (assoc context ::result %))))
    subject-handles))


(defn evaluate-expression*
  [{:keys [db] :as context} expression-def subject-type population-basis]
  (transduce
    (comp
      (partition-all eval-parallel-chunk-size)
      (map #(evaluate-expression** context expression-def % population-basis)))
    (expression-combine-op context)
    (d/type-list db subject-type)))


(defn- missing-expression-anom [name]
  (ba/incorrect
    (format "Missing expression with name `%s`." name)
    :expression-name name))


(defn- expression-def [{:keys [expression-defs]} name]
  (or (get expression-defs name) (missing-expression-anom name)))


(defn- check-context [subject-type {:keys [context name]}]
  (when-not (= subject-type context)
    (ba/incorrect
      (format "The context `%s` of the expression `%s` differs from the subject type `%s`."
              context name subject-type)
      :expression-name name
      :subject-type subject-type
      :expression-context context)))


(defn- def-result-type
  [{result-type-name :resultTypeName
    result-type-specifier :resultTypeSpecifier}]
  (if result-type-name
    (elm-util/parse-type {:type "NamedTypeSpecifier" :name result-type-name})
    (elm-util/parse-type result-type-specifier)))


(defn- check-result-type [population-basis {:keys [name] :as expression-def}]
  (let [result-type (def-result-type expression-def)]
    (if (= :boolean population-basis)
      (when-not (= "Boolean" result-type)
        (ba/incorrect
          (format "The result type `%s` of the expression `%s` differs from the population basis :boolean."
                  result-type name)
          :expression-name name
          :population-basis population-basis
          :expression-result-type result-type))
      (when-not (= (str "List<" population-basis ">") result-type)
        (ba/incorrect
          (format "The result type `%s` of the expression `%s` differs from the population basis `%s`."
                  result-type name population-basis)
          :expression-name name
          :population-basis population-basis
          :expression-result-type result-type)))))


(defn evaluate-expression
  "Evaluates the expression with `name` on each subject of `subject-type`
  available in :db of `context`.

  The context consists of:
   :db - the database to use for obtaining subjects and evaluating the expression
   :now - the evaluation time
   :expression-defs - a map of available expression definitions
   :parameters - an optional map of parameters

  The context of the expression has to match `subject-type`. The result type of
  the expression has to match the `population-basis`.

  Returns a list of subject-handles or an anomaly in case of errors."
  [context name subject-type population-basis]
  (when-ok [expression-def (expression-def context name)
            _ (check-context subject-type expression-def)
            _ (check-result-type population-basis expression-def)]
    (evaluate-expression* context expression-def subject-type population-basis)))


(defn evaluate-individual-expression
  "Evaluates the expression with `name` according to `context`.

  Returns an anomaly in case of errors."
  [context subject-handle name]
  (when-ok [{:keys [name expression]} (expression-def context name)]
    (evaluate-expression-1 context subject-handle name expression)))


(defn- stratum-result-reduce-op [result stratum subject-handle]
  (update result stratum (fnil conj []) subject-handle))


(defn- stratum-combine-op [context]
  (-> (partial merge-with into)
      (wrap-anomaly)
      (wrap-batch-db context)))


(defn- incorrect-stratum-msg [{:keys [id] :as handle} expression-name]
  (format "CQL expression `%s` returned more than one value for resource `%s`."
          expression-name (-> handle fhir-spec/fhir-type name (str "/" id))))


(defn- evaluate-stratum-expression
  [context subject-handle name expression]
  (let [result (evaluate-expression-1 context subject-handle name expression)]
    (if (sequential? result)
      (ba/incorrect (incorrect-stratum-msg subject-handle name))
      result)))


(defn- calc-strata** [context {:keys [name expression]} handles]
  (r/fold
    eval-sequential-chunk-size
    (stratum-combine-op context)
    (fn [context {:keys [subject-handle] :as handle}]
      (if-ok [stratum (evaluate-stratum-expression context subject-handle
                                                 name expression)]
        (update context ::result stratum-result-reduce-op stratum handle)
        #(reduced (assoc context ::result %))))
    handles))


(defn calc-strata* [context expression-def handles]
  (transduce
    (comp
      (partition-all eval-parallel-chunk-size)
      (map (partial calc-strata** context expression-def)))
    (stratum-combine-op context)
    handles))


(defn calc-strata
  "Returns a map of stratum value to a list of subject handles or an anomaly."
  [context expression-name handles]
  (when-ok [expression-def (expression-def context expression-name)]
    (calc-strata* context expression-def handles)))


(defn- calc-function-strata** [context {:keys [name function]} handles]
  (r/fold
    eval-sequential-chunk-size
    (stratum-combine-op context)
    (fn [context {:keys [population-handle subject-handle] :as handle}]
      (if-ok [stratum (evaluate-stratum-expression context subject-handle
                                                   name (function [population-handle]))]
        (update context ::result stratum-result-reduce-op stratum handle)
        #(reduced (assoc context ::result %))))
    handles))


(defn- calc-function-strata* [context function-def handles]
  (transduce
    (comp
      (partition-all eval-parallel-chunk-size)
      (map (partial calc-function-strata** context function-def)))
    (stratum-combine-op context)
    handles))


(defn- missing-function-anom [name]
  (ba/incorrect
    (format "Missing function with name `%s`." name)
    :function-name name))


(defn- function-def [{:keys [function-defs]} name]
  (or (get function-defs name) (missing-function-anom name)))


(defn calc-function-strata
  "Returns a map of stratum value to a list of subject handles or an anomaly."
  [context function-name handles]
  (when-ok [function-def (function-def context function-name)]
    (calc-function-strata* context function-def handles)))


(defn- evaluate-multi-component-stratum-1
  [context
   {:keys [subject-handle population-handle]}
   {:keys [name expression function]}]
  (if function
    (evaluate-stratum-expression context subject-handle name (function [population-handle]))
    (evaluate-stratum-expression context subject-handle name expression)))


(defn- evaluate-multi-component-stratum [context handle defs]
  (transduce
    (comp (map (partial evaluate-multi-component-stratum-1 context handle))
          (halt-when ba/anomaly?))
    conj
    defs))


(defn calc-multi-component-strata** [context defs handles]
  (r/fold
    eval-sequential-chunk-size
    (stratum-combine-op context)
    (fn [context handle]
      (if-ok [stratum (evaluate-multi-component-stratum context handle defs)]
        (update context ::result stratum-result-reduce-op stratum handle)
        #(reduced (assoc context ::result %))))
    handles))


(defn calc-multi-component-strata* [context defs handles]
  (transduce
    (comp
      (partition-all eval-parallel-chunk-size)
      (map (partial calc-multi-component-strata** context defs)))
    (stratum-combine-op context)
    handles))


(defn- def [{:keys [expression-defs population-basis] :as context} name]
  (or (get expression-defs name)
      (if (string? population-basis)
        (function-def context name)
        (missing-expression-anom name))))


(defn- defs [context names]
  (transduce
    (comp (map (partial def context))
          (halt-when ba/anomaly?))
    conj
    names))


(defn calc-multi-component-strata
  "Returns a map of list of stratum values to a list of subject handles or an
  anomaly."
  [context expression-names handles]
  (when-ok [defs (defs context expression-names)]
    (calc-multi-component-strata* context defs handles)))
