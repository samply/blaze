(ns blaze.fhir.operation.evaluate-measure.cql
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.elm.compiler :as c]
   [blaze.elm.expression :as expr]
   [blaze.elm.resource :as cr]
   [blaze.elm.util :as elm-util]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(def ^:private eval-sequential-chunk-size
  "Size of chunks of resources to evaluate sequential.

  Each chunk of those resources is evaluated sequential and the results of
  multiple of those parallel evaluations are combined afterwards."
  1000)

(defn- evaluate-expression-1-error-msg [expression-name e]
  (format "Error while evaluating the expression `%s`: %s" expression-name
          (ex-message e)))

(defn- evaluate-expression-1* [context subject name expression]
  (try
    (expr/eval context expression subject)
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

(defn evaluate-expression-1
  "Evaluates `expression` with `name` on `subject` in `context`."
  {:arglists '([context subject name expression])}
  [{:keys [interrupted?] :as context} subject name expression]
  (or (interrupted?) (evaluate-expression-1* context subject name expression)))

(defmulti result-xf
  (fn [{:keys [population-basis]} _ _]
    (if (nil? population-basis) :subject-based :population-based)))

(defmethod result-xf :subject-based
  [context name expression]
  (keep
   (fn [subject]
     (when-ok [matches? (evaluate-expression-1 context subject name
                                               expression)]
       (when matches?
         {:population-handle subject :subject-handle subject})))))

(defmethod result-xf :population-based
  [context name expression]
  (mapcat
   (fn [subject]
     (if-ok [population-resources (evaluate-expression-1 context subject
                                                         name expression)]
       (coll/eduction
        (map
         (fn [population-resource]
           {:population-handle population-resource
            :subject-handle subject}))
        population-resources)
       vector))))

(defn- evaluate-expression***
  [{:keys [db] :as context} {:keys [name expression]} subject-handles]
  (with-open [db (d/new-batch-db db)]
    (transduce
     (comp (map (partial cr/mk-resource db))
           (result-xf (assoc context :db db) name expression)
           (halt-when ba/anomaly?))
     ((:reduce-op context) db) subject-handles)))

(defn- evaluate-expression**
  "Returns a CompletableFuture that will complete with the result of evaluating
  the expression of `expression-def` over `subject-handles`.

  The future will be executed on :executor of `context`."
  [{:keys [executor] :as context} expression-def subject-handles]
  (ac/supply-async
   #(evaluate-expression*** context expression-def subject-handles)
   executor))

(defn- evaluate-expression-futures
  [{:keys [db] :as context} expression-def subject-type]
  (into
   []
   (comp
    (partition-all eval-sequential-chunk-size)
    (map (partial evaluate-expression** context expression-def)))
   (d/type-list db subject-type)))

(defn- evaluate-expression* [context expression-def subject-type]
  (log/trace "Evaluate expression" (c/form (:expression expression-def)))
  (let [futures (evaluate-expression-futures context expression-def subject-type)]
    (do-sync [_ (ac/all-of futures)]
      (transduce (map ac/join) (completing (:combine-op context)) futures))))

(defn- check-context
  "Returns an anomaly if `subject-type` differs from :context of
   `expression-def`."
  {:arglists '([subject-type expression-def])}
  [subject-type {:keys [context name]}]
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

(defn- render-result-type [result-type]
  (if (vector? result-type)
    (format "List<%s>" (first result-type))
    result-type))

(defn- check-result-type
  "Returns an anomaly if `population-basis` is not compatible with the result
  type of `expression-def`."
  {:arglists '([population-basis expression-def])}
  [population-basis {:keys [name] :as expression-def}]
  (let [result-type (def-result-type expression-def)]
    (if (nil? population-basis)
      (when-not (= "Boolean" result-type)
        (ba/incorrect
         (format "The result type `%s` of the expression `%s` differs from the population basis :boolean."
                 (render-result-type result-type) name)
         :expression-name name
         :population-basis :boolean
         :expression-result-type result-type))
      (when-not (= [population-basis] result-type)
        (ba/incorrect
         (format "The result type `%s` of the expression `%s` differs from the population basis `%s`."
                 (render-result-type result-type) name population-basis)
         :expression-name name
         :population-basis population-basis
         :expression-result-type result-type)))))

(defn- missing-expression-anom [name]
  (ba/incorrect
   (format "Missing expression with name `%s`." name)
   :expression-name name))

(defn- expression-def [{:keys [expression-defs]} name]
  (or (get expression-defs name) (missing-expression-anom name)))

(defn evaluate-expression
  "Evaluates the expression with `name` on each subject of `subject-type`
  available in :db of `context`.

  The context consists of:
   * :db               - the database to use for obtaining subjects and
                         evaluating the expression
   * :executor         - the executor in which the expression is evaluated
   * :now              - the evaluation time
   * :interrupted?     - a function returning an anomaly if the evaluation
                         should be interrupted
   * :expression-defs  - a map of available expression definitions
   * :parameters       - an optional map of parameters
   * :reduce-op        - a reduce function that gets the result reduced so far
                         and a map of :population-handle and :subject-handle.
                         The function also has to return an initial value if
                         called with no argument
   * :combine-op       - a combine function that gets two already reduced
                         results and returns a new one
   * :population-basis - an optional population basis of a type like `Encounter`

  The context of the expression has to match `subject-type`. The result type of
  the expression has to match the `population-basis`.

  Returns a CompletableFuture that will complete with the result of :combine-op
  or will complete exceptionally with an anomaly in case of errors."
  [{:keys [population-basis] :as context} name subject-type]
  (if-ok [expression-def (expression-def context name)
          _ (check-context subject-type expression-def)
          _ (check-result-type population-basis expression-def)]
    (evaluate-expression* context expression-def subject-type)
    ac/completed-future))

(defn evaluate-individual-expression
  "Evaluates the expression with `name` on `subject` according to `context`.

  The context consists of:
   * :db              - the database to use for obtaining subjects and
                        evaluating the expression
   * :executor        - the executor in which the expression is evaluated
   * :now             - the evaluation time
   * :interrupted?    - a function returning an anomaly if the evaluation
                        should be interrupted
   * :expression-defs - a map of available expression definitions
   * :parameters      - an optional map of parameters
   * :reduce-op       - a reduce function that gets the result reduced so far
                        and a map of :population-handle and :subject-handle.
                        The function also has to return an initial value if
                        called with no argument
   * :combine-op      - a combine function that gets two already reduced
                        results and returns a new one

  Returns an anomaly in case of errors."
  [{:keys [executor reduce-op db] :as context} subject name]
  (if-ok [{:keys [name expression]} (expression-def context name)]
    (ac/supply-async
     #(when-ok [matches? (evaluate-expression-1 context subject name expression)]
        (let [reduce-op (reduce-op db)]
          (cond-> (reduce-op)
            matches?
            (reduce-op {:population-handle subject
                        :subject-handle subject}))))
     executor)
    ac/completed-future))

(defn evaluate-multi-component-stratum [context handle evaluators]
  (into
   []
   (comp (map #(% context handle))
         (halt-when ba/anomaly?))
   evaluators))

(defn- missing-function-anom [name]
  (ba/incorrect
   (format "Missing function with name `%s`." name)
   :function-name name))

(defn- function-def [{:keys [function-defs]} name]
  (or (get function-defs name) (missing-function-anom name)))

(defn- expression-or-function-def
  [{:keys [expression-defs population-basis] :as context} name]
  (or (get expression-defs name)
      (if (string? population-basis)
        (function-def context name)
        (missing-expression-anom name))))

(defn- incorrect-stratum-msg [{:fhir/keys [type] :keys [id]} expression-name]
  (format "CQL expression `%s` returned more than one value for resource `%s/%s`."
          expression-name (name type) id))

(defn- evaluate-stratum-expression [context subject name expression]
  (let [result (evaluate-expression-1* context subject name expression)]
    (if (sequential? result)
      (ba/incorrect (incorrect-stratum-msg subject name))
      result)))

(defn stratum-expression-evaluator* [{:keys [name expression function]}]
  (if function
    (fn [context {:keys [subject-handle population-handle]}]
      (evaluate-stratum-expression context subject-handle name (function [population-handle])))
    (fn [context {:keys [subject-handle]}]
      (evaluate-stratum-expression context subject-handle name expression))))

(defn stratum-expression-evaluator [context name]
  (when-ok [def (expression-or-function-def context name)]
    (stratum-expression-evaluator* def)))

(defn stratum-expression-evaluators [context names]
  (into
   []
   (comp (map (partial stratum-expression-evaluator context))
         (halt-when ba/anomaly?))
   names))
