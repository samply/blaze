(ns blaze.elm.compiler.retrieve
  (:require
    [blaze.anomaly :refer [throw-anom]]
    [blaze.datomic.cql :as cql]
    [blaze.datomic.util :as datomic-util]
    [blaze.elm.code :refer [code?]]
    [blaze.elm.compiler.protocols :refer [Expression -eval expr?]]
    [blaze.elm.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds])
  (:import
    [datomic Datom]))


(defrecord AttrRetrieveExpression [attr]
  Expression
  (-eval [_ {:keys [db]} resource _]
    (transduce
      (map #(d/entity db (.v ^Datom %)))
      conj
      (d/datoms db :eavt (:db/id resource) attr))))


(defrecord RevAttrRetrieveExpression [attr]
  Expression
  (-eval [_ {:keys [db]} resource _]
    (transduce
      (map #(d/entity db (.e ^Datom %)))
      conj
      (d/datoms db :vaet (:db/id resource) attr))))


(defn- index-attr
  "Index attribute like :Patient.Observation.code/system|code"
  [context data-type property {:keys [system code]}]
  (let [ns (format "%s.%s.%s" context data-type property)]
    (keyword ns (str system "|" code))))


(s/fdef single-code-expr
  :args (s/cat :db ::ds/db
               :context :elm/expression-execution-context
               :data-type string?
               :property string?
               :code code?)
  :ret expr?)

(defn single-code-expr
  "Returns an expression which, when evaluated, returns all resources of type
  `data-type` which have a code equivalent to `code` at `property` and are
  reachable through `context`.

  Example:
  * data-type - \"Observation\"
  * property - \"code\"
  * code - (code/to-code \"http://loinc.org\" nil \"39156-5\")"
  {:arglists '([db context data-type property code])}
  [db context data-type property code]
  (when-let [attr-id (d/entid db (index-attr context data-type property code))]
    (->AttrRetrieveExpression attr-id)))


(defrecord MultipleCodeRetrieveExpression [exprs]
  Expression
  (-eval [_ context resource _]
    (transduce (mapcat #(-eval % context resource nil)) conj exprs)))


(s/fdef multiple-code-expr
  :args (s/cat :db ::ds/db
               :context :elm/expression-execution-context
               :data-type string?
               :property string?
               :codes (s/coll-of code?))
  :ret expr?)

(defn multiple-code-expr
  "Returns an expression which, when evaluated, returns all resources of type
  `data-type` which have one of the `codes` at `property` and are reachable
  through `context`

  Example:
  * data-type - \"Observation\"
  * property - \"code\"
  * codes - [{:code/id \"http://loinc.org||39156-5\"}]"
  [db context data-type property codes]
  (let [single-code-expr #(single-code-expr db context data-type property %)]
    (->MultipleCodeRetrieveExpression (map single-code-expr codes))))


(s/fdef context-expr
  :args (s/cat :db ::ds/db
               :context :elm/expression-execution-context
               :data-type string?))

;; TODO: use https://www.hl7.org/fhir/compartmentdefinition-patient.html
(defn context-expr
  "Returns an retrieve expression which returns a list of resources of
  `data-type` related to the resource in execution `context`."
  [db context data-type]
  (case context
    "Patient"
    (->RevAttrRetrieveExpression (d/entid db (keyword data-type "subject")))
    "Specimen"
    (case data-type
      "Patient"
      (->AttrRetrieveExpression (d/entid db :Specimen/subject))
      "Observation"
      (->RevAttrRetrieveExpression (d/entid db :Observation/specimen))
      (throw-anom
        ::anom/unsupported
        (format "Unsupported data type `%s` in context `%s`." data-type
                context)
        :elm/expression-execution-context context
        :elm.retrieve/dataType data-type))
    (throw-anom
      ::anom/unsupported
      (format "Unsupported execution context `%s`." context)
      :elm/expression-execution-context context
      :elm.retrieve/dataType data-type)))


(defrecord ResourceRetrieveExpression []
  Expression
  (-eval [_ _ resource _]
    [resource]))


(def resource-expr
  (->ResourceRetrieveExpression))


(defrecord WithRelatedContextRetrieveExpression
  [related-context-expr data-type]
  Expression
  (-eval [_ {:keys [db] :as context} resource scope]
    (when-let [context-resource (-eval related-context-expr context resource scope)]
      (-eval
        (context-expr db (datomic-util/entity-type context-resource) data-type)
        context
        context-resource
        scope))))


(defrecord WithRelatedContextSingleCodeRetrieveExpression
  [related-context-expr data-type code-property code]
  Expression
  (-eval [_ {:keys [db] :as context} resource scope]
    (when-let [context-resource (-eval related-context-expr context resource scope)]
      (-eval
        (single-code-expr
          db (datomic-util/entity-type context-resource) data-type
          code-property code)
        context
        context-resource
        scope))))


(defrecord WithRelatedContextMultipleCodesRetrieveExpression
  [related-context-expr data-type code-property codes]
  Expression
  (-eval [_ {:keys [db] :as context} resource scope]
    (when-let [context-resource (-eval related-context-expr context resource scope)]
      (-eval
        (multiple-code-expr
          db (datomic-util/entity-type context-resource) data-type
          code-property codes)
        context
        context-resource
        scope))))


(s/fdef with-related-context-expr
  :args (s/cat :related-context-expr expr?
               :data-type string?
               :code-property string?
               :codes (s/coll-of code?)))

(defn with-related-context-expr
  [related-context-expr data-type code-property codes]
  (if (seq codes)
    (let [[code & more] (remove nil? codes)]
      (cond
        (nil? code)
        []

        (empty? more)
        (->WithRelatedContextSingleCodeRetrieveExpression
          related-context-expr data-type code-property code)

        :else
        (->WithRelatedContextMultipleCodesRetrieveExpression
          related-context-expr data-type code-property codes)))
    (->WithRelatedContextRetrieveExpression related-context-expr data-type)))


(defn- find-code [db {:keys [system version code]}]
  (when (and system code)
    (:db/id (cql/find-code db system version code))))


(defn expr [eval-context db data-type code-property codes]
  (let [unspecified-eval-context? (= "Unspecified" eval-context)]
    (cond
      (seq codes)
      (if unspecified-eval-context?
        (reify Expression
          (-eval [_ {:keys [db]} _ _]
            (cql/list-resource-by-code
              db data-type code-property (keep #(find-code db %) codes))))

        (let [[code & more] codes]
          (if (empty? more)
            (single-code-expr
              db eval-context data-type code-property code)
            (multiple-code-expr
              db eval-context data-type code-property codes))))

      (nil? codes)
      (if unspecified-eval-context?
        (reify Expression
          (-eval [_ {:keys [db]} _ _]
            (into [] (datomic-util/list-resources db data-type))))

        (if (= data-type eval-context)
          resource-expr
          (context-expr db eval-context data-type)))

      :else
      [])))
