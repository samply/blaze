(ns blaze.elm.compiler.retrieve
  (:require
    [blaze.anomaly :refer [throw-anom]]
    [blaze.elm.compiler.protocols :refer [Expression -eval expr?]]
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


(defn code? [x]
  (contains? x :code/id))


(s/fdef single-code-expr
  :args (s/cat :db ::ds/db :context string? :data-type string? :property string?
               :code code?)
  :ret expr?)

(defn single-code-expr
  "Returns an expression which, when evaluated, returns all resources of type
  `data-type` which have the `code` at `property` and are reachable through
  `context`.

  Example:
  * data-type - \"Observation\"
  * property - \"code\"
  * code - {:code/id \"http://loinc.org||39156-5\"}"
  {:arglists '([db context data-type property code])}
  [db context data-type property {code-id :code/id}]
  (let [ns (format "%s.%s.%s" context data-type property)]
    (if-let [attr-id (d/entid db (keyword ns code-id))]
      (->AttrRetrieveExpression attr-id)
      (throw-anom
        ::anom/fault
        (str "Missing Datomic attribute: " (keyword ns code-id))))))


(defrecord MultipleCodeRetrieveExpression [exprs]
  Expression
  (-eval [_ context resource _]
    (transduce (mapcat #(-eval % context resource nil)) conj exprs)))


(s/fdef multiple-code-expr
  :args (s/cat :db ::ds/db :context string? :data-type string? :property string?
               :code (s/coll-of code?))
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
  :args (s/cat :db ::ds/db :eval-context string? :data-type-name string?))

;; TODO: use https://www.hl7.org/fhir/compartmentdefinition-patient.html
(defn context-expr [db eval-context data-type-name]
  (case eval-context
    "Patient"
    (->RevAttrRetrieveExpression (d/entid db (keyword data-type-name "subject")))
    "Specimen"
    (case data-type-name
      "Patient"
      (->AttrRetrieveExpression (d/entid db :Specimen/subject))
      "Observation"
      (->RevAttrRetrieveExpression (d/entid db :Observation/specimen)))))


(defrecord ResourceRetrieveExpression []
  Expression
  (-eval [_ _ resource _]
    [resource]))


(def resource-expr
  (->ResourceRetrieveExpression))
