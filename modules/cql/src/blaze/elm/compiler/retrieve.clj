(ns blaze.elm.compiler.retrieve
  "Retrieve specific functions.

  https://cql.hl7.org/04-logicalspecification.html#retrieve"
  (:require
    [blaze.anomaly :refer [when-ok]]
    [blaze.db.api :as d]
    [blaze.db.api-spec]
    [blaze.elm.code :refer [code?]]
    [blaze.elm.compiler.protocols :refer [Expression -eval expr?]]
    [blaze.elm.spec]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]))


(set! *warn-on-reflection* true)


(defrecord CompartmentListRetrieveExpression [context data-type]
  Expression
  (-eval [_ {:keys [db]} {:keys [id]} _]
    (into [] (d/list-compartment-resources db context id data-type))))


(defrecord CompartmentQueryRetrieveExpression [batch-fn]
  Expression
  (-eval [_ {:keys [db]} {:keys [id]} _]
    (into [] (batch-fn db id))))


(defn- code->clause-value [{:keys [system code]}]
  (str system "|" code))


(defn- codes->clause-value [codes]
  (str/join "," (map code->clause-value codes)))


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
  (let [clauses [[property (codes->clause-value codes)]]
        res (d/compartment-query-batch node context data-type clauses)]
    (if (::anom/category res)
      (throw (ex-info (::anom/message res) res))
      (->CompartmentQueryRetrieveExpression res))))


;; TODO: find a better solution than hard coding this case
(defrecord SpecimenPatientExpression []
  Expression
  (-eval [_ {:keys [db]} {{:keys [reference]} :subject} _]
    (when reference
      (let [[type id] (str/split reference #"/" 2)]
        (when (= "Patient" type)
          (when (s/valid? :blaze.resource/id id)
            (let [patient (d/resource db "Patient" id)]
              (when-not (d/deleted? patient)
                [patient]))))))))


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


(defrecord WithRelatedContextCodeRetrieveExpression
  [related-context-expr data-type code-property codes]
  Expression
  (-eval [_ {:keys [db] :as context} resource scope]
    (when-let [context-resource (-eval related-context-expr context resource scope)]
      (when-ok [res (d/compartment-query
                      db (:resourceType context-resource) (:id context-resource)
                      data-type [[code-property (codes->clause-value codes)]])]
        (if (::anom/category res)
          (throw (ex-info (::anom/message res) res))
          (into [] res))))))


(s/fdef with-related-context-expr
  :args (s/cat :related-context-expr expr?
               :data-type string?
               :code-property string?
               :codes (s/nilable (s/coll-of code?)))
  :ret expr?)

(defn with-related-context-expr
  [related-context-expr data-type code-property codes]
  (if (seq codes)
    (->WithRelatedContextCodeRetrieveExpression
      related-context-expr data-type code-property codes)
    (->WithRelatedContextRetrieveExpression related-context-expr data-type)))


(defn- unspecified-context-retrieve-expr [data-type code-property codes]
  (if (empty? codes)
    (reify Expression
      (-eval [_ {:keys [db]} _ _]
        (into [] (d/list-resources db data-type))))
    (let [clauses [[code-property codes]]]
      (reify Expression
        (-eval [_ {:keys [db]} _ _]
          (let [res (d/type-query db data-type clauses)]
            (if (::anom/category res)
              (throw (ex-info (::anom/message res) res))
              (into [] res))))))))


(defn- retrieve-expr [node eval-context data-type code-property codes]
  (if (empty? codes)
    (if (= data-type eval-context)
      resource-expr
      (context-expr eval-context data-type))
    (code-expr node eval-context data-type code-property codes)))


(s/fdef expr
  :args (s/cat :node :blaze.db/node
               :eval-context string?
               :data-type string?
               :code-property string?
               :codes (s/nilable (s/coll-of code?)))
  :ret expr?)

(defn expr [node eval-context data-type code-property codes]
  (if (= "Unspecified" eval-context)
    (unspecified-context-retrieve-expr data-type code-property codes)
    (retrieve-expr node eval-context data-type code-property codes)))
