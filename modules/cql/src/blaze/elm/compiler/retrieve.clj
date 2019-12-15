(ns blaze.elm.compiler.retrieve
  "Retrieve specific functions.

  These are Datomic specific.

  https://cql.hl7.org/04-logicalspecification.html#retrieve"
  (:require
    [blaze.anomaly :refer [throw-anom]]
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


;; An expression which traverses `attr` from the resource in context.
;;
;; Returns a vector of resources reachable through `attr`.
(defrecord AttrRetrieveExpression [attr]
  Expression
  (-eval [_ {:keys [db]} resource _]
    (transduce
      (map #(d/entity db (.v ^Datom %)))
      conj
      (d/datoms db :eavt (:db/id resource) attr))))


;; Like AttrRetrieveExpression but goes the attribute in reverse direction.
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

  Uses special index attributes like :Patient.Observation.code/system|code.

  Example:
  * data-type - \"Observation\"
  * property - \"code\"
  * code - (code/to-code \"http://loinc.org\" nil \"39156-5\")"
  [db context data-type property code]
  (when-let [attr-id (d/entid db (index-attr context data-type property code))]
    (->AttrRetrieveExpression attr-id)))


;; Concatenates retrieves from all `exprs` together into one vector.
(defrecord RetrieveAllExpression [exprs]
  Expression
  (-eval [_ context resource _]
    (transduce (mapcat #(-eval % context resource nil)) conj exprs)))


(s/fdef multiple-codes-expr
  :args (s/cat :db ::ds/db
               :context :elm/expression-execution-context
               :data-type string?
               :property string?
               :codes (s/nilable (s/coll-of code?)))
  :ret expr?)

(defn multiple-codes-expr
  "Returns an expression which, when evaluated, returns all resources of type
  `data-type` which have a code equivalent on of the `codes` at `property` and
  are reachable through `context`.

  Uses special index attributes like :Patient.Observation.code/system|code.

  Example:
  * data-type - \"Observation\"
  * property - \"code\"
  * codes - [{:code/id \"http://loinc.org||39156-5\"}]"
  [db context data-type property codes]
  (let [single-code-expr #(single-code-expr db context data-type property %)]
    (->RetrieveAllExpression (mapv single-code-expr codes))))


(s/fdef context-expr
  :args (s/cat :db ::ds/db
               :context :elm/expression-execution-context
               :data-type string?)
  :ret expr?)

;; TODO: use https://www.hl7.org/fhir/compartmentdefinition-patient.html
(defn context-expr
  "Returns an expression which, when evaluated, returns all resources of type
  `data-type` related to the resource in execution `context`."
  [db context data-type]
  (case context
    "Patient"
    (->RevAttrRetrieveExpression
      (d/entid db (keyword (str "Reference." data-type) "subject")))
    "Specimen"
    (case data-type
      "Patient"
      (->AttrRetrieveExpression (d/entid db :Reference.Specimen/subject))
      "Observation"
      (->RevAttrRetrieveExpression (d/entid db :Reference.Observation/specimen))
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


(def ^:private resource-expr
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
        (multiple-codes-expr
          db (datomic-util/entity-type context-resource) data-type
          code-property codes)
        context
        context-resource
        scope))))


(s/fdef with-related-context-expr
  :args (s/cat :related-context-expr expr?
               :data-type string?
               :code-property string?
               :codes (s/nilable (s/coll-of code?)))
  :ret expr?)

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
    (:db/id (d/entity db [:code/id (str system "|" version "|" code)]))))


(defn- list-resource-by-code
  [db data-type-name code-property-name codes]
  (let [code-index-attr (keyword (str data-type-name ".index") code-property-name)]
    (into
      []
      (comp
        (mapcat #(d/datoms db :vaet % code-index-attr))
        (map (fn [[e]] (d/entity db e))))
      codes)))


(defn- unspecified-context-retrieve-expr [db data-type code-property codes]
  (if (empty? codes)
    (reify Expression
      (-eval [_ {:keys [db]} _ _]
        (into [] (datomic-util/list-resources db data-type))))
    (let [codes (keep #(find-code db %) codes)]
      (reify Expression
        (-eval [_ {:keys [db]} _ _]
          (list-resource-by-code db data-type code-property codes))))))


(defn- retrieve-expr [db eval-context data-type code-property codes]
  (if (empty? codes)
    (if (= data-type eval-context)
      resource-expr
      (context-expr db eval-context data-type))
    (let [[code & more] codes]
      (if (empty? more)
        (single-code-expr
          db eval-context data-type code-property code)
        (multiple-codes-expr
          db eval-context data-type code-property codes)))))


(s/fdef expr
  :args (s/cat :eval-context string?
               :db ::ds/db
               :data-type string?
               :code-property string?
               :codes (s/nilable (s/coll-of code?)))
  :ret expr?)

(defn expr [eval-context db data-type code-property codes]
  (if (= "Unspecified" eval-context)
    (unspecified-context-retrieve-expr db data-type code-property codes)
    (retrieve-expr db eval-context data-type code-property codes)))
