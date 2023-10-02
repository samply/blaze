(ns blaze.fhir.operation.evaluate-measure.cql
  (:require
    [blaze.anomaly :as ba :refer [if-ok when-ok]]
    [blaze.async.comp :as ac :refer [do-sync]]
    [blaze.coll.core :as coll]
    [blaze.db.api :as d]
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.external-data :as ed]
    [blaze.elm.expression :as expr]
    [blaze.elm.util :as elm-util]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.util :refer [conj-vec]]
    [taoensso.timbre :as log])
  (:import
    [java.time Duration]))


(set! *warn-on-reflection* true)


(def eval-sequential-chunk-size
  "Size of chunks of resources to evaluate sequential.

  Each chunk of those resources if evaluated sequential and the results of
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


(defn- timeout-millis [{:keys [timeout]}]
  (.toMillis ^Duration timeout))


(defn- timeout-eclipsed-msg [context]
  (format "Timeout of %d millis eclipsed while evaluating."
          (timeout-millis context)))


(defn evaluate-expression-1
  {:arglists '([context subject name expression])}
  [{:keys [timeout-eclipsed?] :as context} subject name expression]
  (if (timeout-eclipsed?)
    (ba/interrupted
      (timeout-eclipsed-msg context)
      :timeout (:timeout context))
    (evaluate-expression-1* context subject name expression)))


(defmulti result-xf
  (fn [{:keys [return-handles? population-basis]} _]
    [(if return-handles? :return-handles :count)
     (if (nil? population-basis) :subject-based :population-based)]))


(defmethod result-xf [:return-handles :subject-based]
  [context {:keys [name expression]}]
  (keep
    (fn [subject]
      (when-ok [matches? (evaluate-expression-1 context subject name
                                                expression)]
        (when matches?
          {:population-handle subject :subject-handle subject})))))


(defmethod result-xf [:return-handles :population-based]
  [context {:keys [name expression]}]
  (mapcat
    (fn [subject]
      (when-ok [population-resources (evaluate-expression-1 context subject
                                                            name expression)]
        (coll/eduction
          (map
            (fn [population-resource]
              {:population-handle population-resource
               :subject-handle subject}))
          population-resources)))))


(defmethod result-xf [:count :subject-based]
  [context {:keys [name expression]}]
  (map
    (fn [subject]
      (when-ok [matches? (evaluate-expression-1 context subject name
                                                expression)]
        (if matches? 1 0)))))


(defmethod result-xf [:count :population-based]
  [context {:keys [name expression]}]
  (map
    (fn [subject]
      (when-ok [population-resources (evaluate-expression-1 context subject
                                                            name expression)]
        (count population-resources)))))


(defn- reduce-op [{:keys [return-handles?]}]
  (if return-handles? conj +))


(defn- combine-op [{:keys [return-handles?]}]
  (if return-handles? into +))


(defn- evaluate-expression***
  [{:keys [db] :as context} expression-def subject-handles]
  (with-open [db (d/new-batch-db db)]
    (transduce
      (comp (map (partial ed/mk-resource db))
            (result-xf (assoc context :db db) expression-def)
            (halt-when ba/anomaly?))
      (reduce-op context) subject-handles)))


(defn- evaluate-expression**
  "Evaluates the expression within `def` over `subject-handles` parallel.

  Subject handles have to be a vector in order to ensure parallel execution."
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
      (transduce (map ac/join) (combine-op context) futures))))


(defn- missing-expression-anom [name]
  (ba/incorrect
    (format "Missing expression with name `%s`." name)
    :expression-name name))


(defn- expression-def [{:keys [expression-defs]} name]
  (or (get expression-defs name) (missing-expression-anom name)))


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
                  result-type name)
          :expression-name name
          :population-basis :boolean
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
   * context :db                - the database to use for obtaining subjects and
                                  evaluating the expression
   * context :executor          - the executor in which the expression is evaluated 
   * context :now               - the evaluation time
   * context :timeout-eclipsed? - a function returning ture if the evaluation
                                  timeout is eclipsed
   * context :timeout           - the evaluation timeout itself
   * context :expression-defs   - a map of available expression definitions
   * context :parameters        - an optional map of parameters
   * context :return-handles?   - whether subject-handles or a simple count
                                  should be returned
   * context :population-basis  - the population basis of either :boolean or a
                                  type like `Encounter`
   * name                       - the name of the expression
   * subject-type               - the type of subjects like `Patient`

  The context of the expression has to match `subject-type`. The result type of
  the expression has to match the `population-basis`.

  Returns a CompletableFuture that will complete with a list of subject-handles
  or a simple count or will complete exceptionally with an anomaly in\n  case of
  errors."
  [{:keys [population-basis] :as context} name subject-type]
  (if-ok [expression-def (expression-def context name)
          _ (check-context subject-type expression-def)
          _ (check-result-type population-basis expression-def)]
    (evaluate-expression* context expression-def subject-type)
    ac/completed-future))


(defn evaluate-individual-expression
  "Evaluates the expression with `name` on `subject` according to `context`.

  The context consists of:
   * context :db                - the database to use for obtaining subjects and
                                  evaluating the expression
   * context :executor          - the executor in which the expression is evaluated 
   * context :now               - the evaluation time
   * context :timeout-eclipsed? - a function returning ture if the evaluation
                                  timeout is eclipsed
   * context :timeout           - the evaluation timeout itself
   * context :expression-defs   - a map of available expression definitions
   * context :parameters        - an optional map of parameters
   * context :return-handles?   - whether subject-handles or a simple count
                                  should be returned

  Returns an anomaly in case of errors."
  [{:keys [executor return-handles?] :as context} subject name]
  (if-ok [{:keys [name expression]} (expression-def context name)]
    (ac/supply-async
      #(when-ok [matches? (evaluate-expression-1 context subject name expression)]
         (if matches?
           (if return-handles?
             [{:population-handle subject
               :subject-handle subject}]
             1)
           (if return-handles? [] 0)))
      executor)
    ac/completed-future))


(defn- incorrect-stratum-msg [{:keys [id] :as resource} expression-name]
  (format "CQL expression `%s` returned more than one value for resource `%s/%s`."
          expression-name (-> resource fhir-spec/fhir-type name) id))


(defn- evaluate-stratum-expression [context subject name expression]
  (let [result (evaluate-expression-1 context subject name expression)]
    (if (sequential? result)
      (ba/incorrect (incorrect-stratum-msg subject name))
      result)))


(defn- calc-strata***
  [{:keys [db] :as context} {:keys [name expression]} handles]
  (with-open [db (d/new-batch-db db)]
    (let [context (assoc context :db db)]
      (reduce
        (fn [ret {:keys [subject-handle] :as handle}]
          (if-ok [stratum (evaluate-stratum-expression context subject-handle
                                                       name expression)]
            (update ret stratum conj-vec handle)
            reduced))
        {} handles))))


(defn- calc-strata** [{:keys [executor] :as context} expression-def handles]
  (ac/supply-async
    #(calc-strata*** context expression-def handles)
    executor))


(defn- calc-strata-futures [context expression-def handles]
  (into
    []
    (comp
      (partition-all eval-sequential-chunk-size)
      (map (partial calc-strata** context expression-def)))
    handles))


(defn- calc-strata* [context expression-def handles]
  (let [futures (calc-strata-futures context expression-def handles)]
    (do-sync [_ (ac/all-of futures)]
      (transduce (map ac/join) (partial merge-with into) futures))))


(defn calc-strata
  "Returns a CompletableFuture that will complete with a map of stratum value to
  a list of subject handles or will complete exceptionally with an anomaly in
  case of errors."
  [context expression-name handles]
  (if-ok [expression-def (expression-def context expression-name)]
    (calc-strata* context expression-def handles)
    ac/completed-future))


(defn- calc-function-strata***
  [{:keys [db] :as context} {:keys [name function]} handles]
  (with-open [db (d/new-batch-db db)]
    (let [context (assoc context :db db)]
      (reduce
        (fn [ret {:keys [population-handle subject-handle] :as handle}]
          (if-ok [stratum (evaluate-stratum-expression context subject-handle
                                                       name (function [population-handle]))]
            (update ret stratum conj-vec handle)
            reduced))
        {} handles))))


(defn- calc-function-strata**
  [{:keys [executor] :as context} function-def handles]
  (ac/supply-async
    #(calc-function-strata*** context function-def handles)
    executor))


(defn- calc-function-strata-futures [context function-def handles]
  (into
    []
    (comp
      (partition-all eval-sequential-chunk-size)
      (map (partial calc-function-strata** context function-def)))
    handles))


(defn- calc-function-strata* [context function-def handles]
  (let [futures (calc-function-strata-futures context function-def handles)]
    (do-sync [_ (ac/all-of futures)]
      (transduce (map ac/join) (partial merge-with into) futures))))


(defn- missing-function-anom [name]
  (ba/incorrect
    (format "Missing function with name `%s`." name)
    :function-name name))


(defn- function-def [{:keys [function-defs]} name]
  (or (get function-defs name) (missing-function-anom name)))


(defn calc-function-strata
  "Returns a CompletableFuture that will complete with a map of stratum value to
  a list of subject handles or will complete exceptionally with an anomaly in
  case of errors."
  [context function-name handles]
  (if-ok [function-def (function-def context function-name)]
    (calc-function-strata* context function-def handles)
    ac/completed-future))


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


(defn calc-multi-component-strata*** [{:keys [db] :as context} defs handles]
  (with-open [db (d/new-batch-db db)]
    (let [context (assoc context :db db)]
      (reduce
        (fn [ret handle]
          (if-ok [stratum (evaluate-multi-component-stratum context handle defs)]
            (update ret stratum conj-vec handle)
            reduced))
        {} handles))))


(defn- calc-multi-component-strata**
  [{:keys [executor] :as context} defs handles]
  (ac/supply-async
    #(calc-multi-component-strata*** context defs handles)
    executor))


(defn- calc-multi-component-strata-futures [context defs handles]
  (into
    []
    (comp
      (partition-all eval-sequential-chunk-size)
      (map (partial calc-multi-component-strata** context defs)))
    handles))


(defn calc-multi-component-strata* [context defs handles]
  (let [futures (calc-multi-component-strata-futures context defs handles)]
    (do-sync [_ (ac/all-of futures)]
      (transduce (map ac/join) (partial merge-with into) futures))))


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
  "Returns a CompletableFuture that will complete with a map of stratum value to
  a list of subject handles or will complete exceptionally with an anomaly in
  case of errors."
  [context expression-names handles]
  (if-ok [defs (defs context expression-names)]
    (calc-multi-component-strata* context defs handles)
    ac/completed-future))
