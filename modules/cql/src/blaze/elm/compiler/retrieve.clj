(ns blaze.elm.compiler.retrieve
  "Retrieve specific functions.

  https://cql.hl7.org/04-logicalspecification.html#retrieve"
  (:require
    [blaze.db.api :as d]
    [blaze.db.api-spec]
    [blaze.elm.compiler.protocols :refer [Expression -eval]]
    [blaze.elm.spec]
    [blaze.elm.util :as elm-util]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]))


(set! *warn-on-reflection* true)


(defrecord CompartmentListRetrieveExpression [context data-type]
  Expression
  (-eval [_ {:keys [db]} {:keys [id]} _]
    (d/list-compartment-resources db context id data-type)))


(defrecord CompartmentQueryRetrieveExpression [query]
  Expression
  (-eval [_ {:keys [db]} {:keys [id]} _]
    (d/execute-query db query id)))


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
  (let [clauses [(cons property (map code->clause-value codes))]
        query (d/compile-compartment-query node context data-type clauses)]
    (->CompartmentQueryRetrieveExpression query)))


(defn- split-reference [s]
  (when-let [idx (str/index-of s \/)]
    [(subs s 0 idx) (subs s (inc idx))]))


;; TODO: find a better solution than hard coding this case
(defrecord SpecimenPatientExpression []
  Expression
  (-eval [_ {:keys [db]} {{:keys [reference]} :subject} _]
    (when reference
      (when-let [[type id] (split-reference reference)]
        (when (and (= "Patient" type) (string? id))
          (let [patient (d/resource db "Patient" id)]
            (when-not (d/deleted? patient)
              [patient])))))))


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
  Expression
  (-eval [_ _ resource _]
    [resource]))


(def ^:private resource-expr
  (->ResourceRetrieveExpression))


(defrecord WithRelatedContextRetrieveExpression
  [related-context-expr data-type]
  Expression
  (-eval [_ context resource scope]
    (when-let [context-resource (-eval related-context-expr context resource scope)]
      (-eval
        (context-expr (:resourceType context-resource) data-type)
        context
        context-resource
        scope))))


(defrecord WithRelatedContextQueryRetrieveExpression
  [context-expr query]
  Expression
  (-eval [_ {:keys [db] :as context} resource scope]
    (when-let [{:keys [id]} (-eval context-expr context resource scope)]
      (when (string? id)
        (d/execute-query db query id)))))


(defn- compartment-query [db code id type clauses]
  (let [res (d/compartment-query db code id type clauses)]
    (if (::anom/category res)
      (throw (ex-info (::anom/message res) res))
      res)))


(defrecord WithRelatedContextCodeRetrieveExpression
  [context-expr data-type clauses]
  Expression
  (-eval [_ {:keys [db] :as context} resource scope]
    (when-let [{type :resourceType :keys [id]} (-eval context-expr context resource scope)]
      (when (and (string? type) (string? id))
        (compartment-query db type id data-type clauses)))))


(defn with-related-context-expr
  [node context-expr data-type code-property codes]
  (if (seq codes)
    (if-let [result-type-name (:result-type-name (meta context-expr))]
      (let [[value-type-ns context-type] (elm-util/parse-qualified-name result-type-name)]
        (if (= "http://hl7.org/fhir" value-type-ns)
          (let [clauses [(cons code-property (map code->clause-value codes))]
                query (d/compile-compartment-query node context-type data-type clauses)]
            (if (::anom/category query)
              (throw (ex-info (::anom/message query) query))
              (->WithRelatedContextQueryRetrieveExpression context-expr query)))

          (->WithRelatedContextCodeRetrieveExpression
            context-expr data-type
            [(cons code-property (map code->clause-value codes))])))
      (->WithRelatedContextCodeRetrieveExpression
        context-expr data-type
        [(cons code-property (map code->clause-value codes))]))
    (->WithRelatedContextRetrieveExpression context-expr data-type)))


(defn- unspecified-context-retrieve-expr [node data-type code-property codes]
  (if (empty? codes)
    (reify Expression
      (-eval [_ {:keys [db]} _ _]
        (into [] (d/list-resources db data-type))))
    (let [query (d/compile-type-query node data-type [[code-property codes]])]
      (if (::anom/category query)
        (throw (ex-info (::anom/message query) query))
        (reify Expression
          (-eval [_ {:keys [db]} _ _]
            (into [] (d/execute-query db query))))))))


(defn- retrieve-expr [node eval-context data-type code-property codes]
  (if (empty? codes)
    (if (= data-type eval-context)
      resource-expr
      (context-expr eval-context data-type))
    (code-expr node eval-context data-type code-property codes)))


(defn expr [node eval-context data-type code-property codes]
  (if (= "Unspecified" eval-context)
    (unspecified-context-retrieve-expr node data-type code-property codes)
    (retrieve-expr node eval-context data-type code-property codes)))
