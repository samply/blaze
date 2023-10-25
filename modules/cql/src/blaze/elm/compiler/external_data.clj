(ns blaze.elm.compiler.external-data
  "11. External Data

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.anomaly :as ba :refer [if-ok]]
    [blaze.coll.core :as coll]
    [blaze.db.api :as d]
    [blaze.db.impl.index.resource-handle :as rh]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.structured-values]
    [blaze.elm.spec]
    [blaze.elm.util :as elm-util]
    [blaze.fhir.spec.type.protocols :as p]
    [clojure.string :as str]
    [prometheus.alpha :as prom :refer [defcounter]])
  (:import
    [blaze.elm.compiler.structured_values SourcePropertyExpression]
    [clojure.lang ILookup]
    [java.util List]))


(set! *warn-on-reflection* true)


(defcounter retrieve-total
  "Number of times a retrieve expression as evaluated."
  {:namespace "blaze"
   :subsystem "cql"})


;; A resource that is a wrapper of a resource-handle that will lazily pull the
;; resource content if some property other than :id is accessed.
(deftype Resource [db id handle ^long lastChangeT content]
  p/FhirType
  (-type [_]
    (p/-type handle))

  ILookup
  (valAt [r key]
    (.valAt r key nil))
  (valAt [_ key not-found]
    (case key
      :id id
      (-> (or @content (vreset! content @(d/pull-content db handle)))
          (get key not-found))))

  core/Expression
  (-static [_]
    true)
  (-attach-cache [expr _]
    expr)
  (-resolve-refs [expr _]
    expr)
  (-resolve-params [expr _]
    expr)
  (-eval [expr _ _ _]
    expr)
  (-form [_]
    (list 'resource (name (p/-type handle)) id (rh/t handle)))

  Object
  (toString [_]
    (str (name (p/-type handle)) "[id = " id ", t = " (rh/t handle) ", last-change-t = " lastChangeT "]")))


(defn resource? [x]
  (instance? Resource x))


(defn- patient-last-change-t [db handle]
  (or (d/patient-compartment-last-change-t db (rh/id handle)) (rh/t handle)))


(defn- last-change-t [db handle]
  (if (identical? :fhir/Patient (p/-type handle))
    (patient-last-change-t db handle)
    (d/t db)))


(defn mk-resource [db handle]
  (Resource. db (rh/id handle) handle (last-change-t db handle) (volatile! nil)))


(defn resource-mapper [db]
  (map (partial mk-resource db)))


(defn- code->clause-value [{:keys [system code]}]
  (str system "|" code))


(defprotocol ToClauses
  (-to-clauses [x property]))


(extend-protocol ToClauses
  List
  (-to-clauses [codes property]
    [(into [property] (map code->clause-value) codes)])

  SourcePropertyExpression
  (-to-clauses [codes property]
    (-to-clauses (core/-eval codes nil nil nil) property)))


(defn- code-expr
  "Returns an expression which, when evaluated, returns all resources of type
  `data-type` which have a code equivalent to `code` at `property` and are
  reachable through `context`.

  Uses special index attributes like :Patient.Observation.code/system|code.

  Example:
  * data-type - \"Observation\"
  * property - \"code\"
  * codes - [(code/to-code \"http://loinc.org\" nil \"39156-5\")]"
  [node context data-type property codes]
  (let [clauses (-to-clauses codes property)
        query (d/compile-compartment-query node context data-type clauses)]
    (reify core/Expression
      (-static [_]
        false)
      (-attach-cache [expr _]
        expr)
      (-resolve-refs [expr _]
        expr)
      (-resolve-params [expr _]
        expr)
      (-eval [_ {:keys [db]} {:keys [id]} _]
        (prom/inc! retrieve-total)
        (coll/eduction (resource-mapper db) (d/execute-query db query id)))
      (-form [_]
        `(~'retrieve ~data-type ~(d/query-clauses query))))))


(defn- split-reference [s]
  (when-let [idx (str/index-of s \/)]
    [(subs s 0 idx) (subs s (inc idx))]))


;; TODO: find a better solution than hard coding this case
(defrecord SpecimenPatientExpression []
  core/Expression
  (-static [_]
    false)
  (-attach-cache [expr _]
    expr)
  (-resolve-refs [expr _]
    expr)
  (-resolve-params [expr _]
    expr)
  (-eval [_ {:keys [db]} resource _]
    (prom/inc! retrieve-total)
    (let [{{:keys [reference]} :subject} resource]
      (when reference
        (when-let [[type id] (split-reference reference)]
          (when (and (= "Patient" type) (string? id))
            (let [{:keys [op] :as handle} (d/resource-handle db "Patient" id)]
              (when-not (identical? :delete op)
                [(mk-resource db handle)])))))))
  (-form [_]
    '(retrieve (Specimen) "Patient")))


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
    (reify core/Expression
      (-static [_]
        false)
      (-attach-cache [expr _]
        expr)
      (-resolve-refs [expr _]
        expr)
      (-resolve-params [expr _]
        expr)
      (-eval [_ {:keys [db]} {:keys [id]} _]
        (prom/inc! retrieve-total)
        (coll/eduction
          (resource-mapper db)
          (d/list-compartment-resource-handles db context id data-type)))
      (-form [_]
        `(~'retrieve ~data-type)))))


(def ^:private resource-expr
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [expr _]
      expr)
    (-resolve-refs [expr _]
      expr)
    (-resolve-params [expr _]
      expr)
    (-eval [_ _ resource _]
      [resource])
    (-form [_]
      '(retrieve-resource))))


(defn- unsupported-type-ns-anom [value-type-ns]
  (ba/unsupported (format "Unsupported related context retrieve expression with result type namespace of `%s`." value-type-ns)))


(def ^:private unsupported-related-context-expr-without-type-anom
  (ba/unsupported "Unsupported related context retrieve expression without result type."))


(defn- related-context-expr-without-codes [related-context-expr data-type]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [expr _]
      expr)
    (-resolve-refs [expr _]
      expr)
    (-resolve-params [expr _]
      expr)
    (-eval [_ context resource scope]
      (prom/inc! retrieve-total)
      (when-let [context-resource (core/-eval related-context-expr context resource scope)]
        (core/-eval
          (context-expr (-> context-resource :fhir/type name) data-type)
          context
          context-resource
          scope)))
    (-form [_]
      (list 'retrieve (core/-form related-context-expr) data-type))))


(defn- related-context-expr
  [node context-expr data-type code-property codes]
  (if (seq codes)
    (if-let [result-type-name (:result-type-name (meta context-expr))]
      (let [[value-type-ns context-type] (elm-util/parse-qualified-name result-type-name)]
        (if (= "http://hl7.org/fhir" value-type-ns)
          (let [clauses [(into [code-property] (map code->clause-value) codes)]]
            (if-ok [query (d/compile-compartment-query node context-type data-type clauses)]
              (reify core/Expression
                (-static [_]
                  false)
                (-attach-cache [expr _]
                  expr)
                (-resolve-refs [expr _]
                  expr)
                (-resolve-params [expr _]
                  expr)
                (-eval [_ {:keys [db] :as context} resource scope]
                  (prom/inc! retrieve-total)
                  (when-let [{:keys [id]} (core/-eval context-expr context resource scope)]
                    (when (string? id)
                      (coll/eduction
                        (resource-mapper db)
                        (d/execute-query db query id)))))
                (-form [_]
                  (list 'retrieve (core/-form context-expr) data-type (d/query-clauses query))))
              ba/throw-anom))
          (ba/throw-anom (unsupported-type-ns-anom value-type-ns))))
      (ba/throw-anom unsupported-related-context-expr-without-type-anom))
    (related-context-expr-without-codes context-expr data-type)))


(defn- unfiltered-context-expr [node data-type code-property codes]
  (if (empty? codes)
    (reify core/Expression
      (-static [_]
        false)
      (-attach-cache [expr _]
        expr)
      (-resolve-refs [expr _]
        expr)
      (-resolve-params [expr _]
        expr)
      (-eval [_ {:keys [db]} _ _]
        (prom/inc! retrieve-total)
        (coll/eduction (resource-mapper db) (d/type-list db data-type)))
      (-form [_]
        `(~'retrieve ~data-type)))
    (let [clauses [(into [code-property] (map code->clause-value) codes)]]
      (if-ok [query (d/compile-type-query node data-type clauses)]
        (reify core/Expression
          (-static [_]
            false)
          (-attach-cache [expr _]
            expr)
          (-resolve-refs [expr _]
            expr)
          (-resolve-params [expr _]
            expr)
          (-eval [_ {:keys [db]} _ _]
            (prom/inc! retrieve-total)
            (coll/eduction (resource-mapper db) (d/execute-query db query)))
          (-form [_]
            `(~'retrieve ~data-type ~(d/query-clauses query))))
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
