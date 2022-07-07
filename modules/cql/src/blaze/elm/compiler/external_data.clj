(ns blaze.elm.compiler.external-data
  "11. External Data

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.anomaly :as ba :refer [if-ok]]
    [blaze.db.api :as d]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.spec]
    [blaze.elm.util :as elm-util]
    [clojure.string :as str]))


(set! *warn-on-reflection* true)


(defrecord CompartmentListRetrieveExpression [context data-type]
  core/Expression
  (-eval [_ {:keys [db]} {:keys [id]} _]
    (d/list-compartment-resource-handles db context id data-type))
  (-form [_]
    `(~'compartment-list-retrieve ~data-type)))


(defrecord CompartmentQueryRetrieveExpression [query data-type clauses]
  core/Expression
  (-eval [_ {:keys [db]} {:keys [id]} _]
    (d/execute-query db query id))
  (-form [_]
    `(~'compartment-query-retrieve ~data-type ~clauses)))


(defn- code->clause-value [{:keys [system code]}]
  (str system "|" code))


(defn- code-expr
  "Returns an expression which, when evaluated, returns all resources of type
  `data-type` which have a code equivalent to `code` at `property` and are
  reachable through `context`.

  Uses special index attributes like :Patient.Observation.code/system|code.

  Example:
  * data-type - \"Observation\"
  * property - \"code\"
  * code - (code/to-code \"http://loinc.org\" nil \"39156-5\")"
  [node context data-type property codes]
  (let [clauses [(into [property] (map code->clause-value) codes)]
        query (d/compile-compartment-query node context data-type clauses)]
    (->CompartmentQueryRetrieveExpression query data-type clauses)))


(defn- split-reference [s]
  (when-let [idx (str/index-of s \/)]
    [(subs s 0 idx) (subs s (inc idx))]))


(defn- pull [db x]
  (if (d/resource-handle? x)
    @(d/pull-content db x)
    x))


;; TODO: find a better solution than hard coding this case
(defrecord SpecimenPatientExpression []
  core/Expression
  (-eval [_ {:keys [db]} resource-or-handle _]
    (let [{{:keys [reference]} :subject} (pull db resource-or-handle)]
      (when reference
        (when-let [[type id] (split-reference reference)]
          (when (and (= "Patient" type) (string? id))
            (let [{:keys [op] :as handle} (d/resource-handle db "Patient" id)]
              (when-not (identical? :delete op)
                [@(d/pull-content db handle)]))))))))


(def ^:private specimen-patient-expr
  (->SpecimenPatientExpression))


(defn- context-expr
  "Returns an expression which, when evaluated, returns all resources of type
  `data-type` related to the resource in execution `context`."
  [context data-type]
  (case context
    "Specimen"
    (case data-type
      "Patient"
      specimen-patient-expr)
    (->CompartmentListRetrieveExpression context data-type)))


(defrecord ResourceRetrieveExpression []
  core/Expression
  (-eval [_ _ resource _]
    [resource])
  (-form [_]
    (list 'retrieve-resource)))


(def ^:private resource-expr
  (->ResourceRetrieveExpression))


(defrecord WithRelatedContextRetrieveExpression
  [related-context-expr data-type]
  core/Expression
  (-eval [_ context resource scope]
    (when-let [context-resource (core/-eval related-context-expr context resource scope)]
      (core/-eval
        (context-expr (-> context-resource :fhir/type name) data-type)
        context
        context-resource
        scope))))


(defrecord WithRelatedContextQueryRetrieveExpression
  [context-expr query]
  core/Expression
  (-eval [_ {:keys [db] :as context} resource scope]
    (when-let [{:keys [id]} (core/-eval context-expr context resource scope)]
      (when (string? id)
        (d/execute-query db query id)))))


(defrecord WithRelatedContextCodeRetrieveExpression
  [context-expr data-type clauses]
  core/Expression
  (-eval [_ {:keys [db] :as context} resource scope]
    (when-let [{:fhir/keys [type] :keys [id]}
               (core/-eval context-expr context resource scope)]
      (when-let [type (some-> type name)]
        (when id
          (ba/throw-when
            (d/compartment-query db type id data-type clauses)))))))


(defn related-context-expr
  [node context-expr data-type code-property codes]
  (if (seq codes)
    (if-let [result-type-name (:result-type-name (meta context-expr))]
      (let [[value-type-ns context-type] (elm-util/parse-qualified-name result-type-name)]
        (if (= "http://hl7.org/fhir" value-type-ns)
          (let [clauses [(into [code-property] (map code->clause-value) codes)]]
            (if-ok [query (d/compile-compartment-query node context-type data-type clauses)]
              (->WithRelatedContextQueryRetrieveExpression context-expr query)
              ba/throw-anom))

          (->WithRelatedContextCodeRetrieveExpression
            context-expr data-type
            [(cons code-property (map code->clause-value codes))])))
      (->WithRelatedContextCodeRetrieveExpression
        context-expr data-type
        [(cons code-property (map code->clause-value codes))]))
    (->WithRelatedContextRetrieveExpression context-expr data-type)))


(defn- unfiltered-context-expr [node data-type code-property codes]
  (if (empty? codes)
    (reify core/Expression
      (-eval [_ {:keys [db]} _ _]
        (vec (d/type-list db data-type)))
      (-form [_]
        `(~'retrieve ~data-type)))
    (let [clauses [(into [code-property] (map code->clause-value) codes)]]
      (if-ok [query (d/compile-type-query node data-type clauses)]
        (reify core/Expression
          (-eval [_ {:keys [db]} _ _]
            (vec (d/execute-query db query))))
        ba/throw-anom))))


(defn- expr* [node eval-context data-type code-property codes]
  (if (empty? codes)
    (if (= data-type eval-context)
      resource-expr
      (context-expr eval-context data-type))
    (code-expr node eval-context data-type code-property codes)))


;; 11.1. Retrieve
(defn- expr
  [{:keys [node eval-context]} context-expr data-type code-property codes]
  (cond
    context-expr
    (related-context-expr node context-expr data-type code-property codes)

    (= "Unfiltered" eval-context)
    (unfiltered-context-expr node data-type code-property codes)

    :else
    (expr* node eval-context data-type code-property codes)))


(defn- unsupported-type-namespace-anom [type-ns]
  (ba/unsupported
    (format "Unsupported type namespace `%s` in Retrieve expression." type-ns)
    :type-ns type-ns))


(defmethod core/compile* :elm.compiler.type/retrieve
  [context
   {context-expr :context
    data-type :dataType
    code-property :codeProperty
    codes-expr :codes
    :or {code-property "code"}}]
  (let [[type-ns data-type] (elm-util/parse-qualified-name data-type)]
    (if (= "http://hl7.org/fhir" type-ns)
      (expr
        context
        (some->> context-expr (core/compile* context))
        data-type
        code-property
        (some->> codes-expr (core/compile* context)))
      (ba/throw-anom (unsupported-type-namespace-anom type-ns)))))
