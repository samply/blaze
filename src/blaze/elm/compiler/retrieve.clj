(ns blaze.elm.compiler.retrieve
  (:require
    [blaze.elm.compiler.protocols :refer [Expression -eval expr?]]
    [blaze.util :refer [throw-anom]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds]))


(defrecord SingleCodeRetrieveExpression [attr-id]
  Expression
  (-eval [_ {:keys [db]} resource _]
    (transduce
      (map #(d/entity db (:v %)))
      conj
      (d/datoms db :eavt (:db/id resource) attr-id))))


(defn code? [x]
  #(contains? x :code/id))


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
      (->SingleCodeRetrieveExpression attr-id)
      (throw-anom
        {::anom/category ::anom/fault
         ::anom/message (str "Missing Datomic attribute: " (keyword ns code-id))}))))


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


(defrecord CardOneRetrieveExpression [kw]
  Expression
  (-eval [_ _ resource _]
    [(kw resource)]))


(defrecord CardManyRetrieveExpression [kw]
  Expression
  (-eval [_ _ resource _]
    (kw resource)))


(s/fdef context-expr
  :args (s/cat :eval-context string? :data-type-name string?))

;; TODO: use https://www.hl7.org/fhir/compartmentdefinition-patient.html
(defn context-expr [eval-context data-type-name]
  (case eval-context
    "Patient"
    (->CardManyRetrieveExpression (keyword data-type-name "_subject"))
    "Specimen"
    (case data-type-name
      "Patient"
      (->CardOneRetrieveExpression :Specimen/subject)
      "Observation"
      (->CardManyRetrieveExpression :Observation/_specimen))))
