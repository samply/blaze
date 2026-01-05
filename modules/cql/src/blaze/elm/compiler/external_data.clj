(ns blaze.elm.compiler.external-data
  "11. External Data

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [if-ok throw-anom]]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.elm.code :refer [code?]]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.macros :refer [reify-expr]]
   [blaze.elm.compiler.structured-values]
   [blaze.elm.resource :as cr]
   [blaze.elm.spec]
   [blaze.elm.util :as elm-util]
   [blaze.fhir.spec.references :as fsr]
   [blaze.util :refer [str]]
   [prometheus.alpha :as prom :refer [defcounter]]))

(set! *warn-on-reflection* true)

(defcounter retrieve-total
  "Number of times a retrieve expression was evaluated."
  {:namespace "blaze"
   :subsystem "cql"})

(defn- code->clause-value [{:keys [system code]}]
  (str system "|" code))

(defn- code-expr
  "Returns an expression which, when evaluated, returns all resources of type
  `data-type` which have a code equivalent to `code` at `property` and are
  reachable through `eval-context`.

  Example:
  * data-type - \"Observation\"
  * eval-context - \"Patient\"
  * property - \"code\"
  * codes - [(code \"http://loinc.org\" nil \"39156-5\")]"
  [node eval-context data-type property codes]
  (let [clauses [(into [property] (map code->clause-value) codes)]]
    (if-ok [type-query (d/compile-type-query node data-type clauses)
            compartment-query (d/compile-compartment-query node eval-context
                                                           data-type clauses)]
      (reify-expr core/Expression
        (-optimize [expr db]
         ;; if there is no resource, regardless of the individual patient,
         ;; available, just return an empty list for further optimizations
          (if (coll/empty? (d/execute-query db type-query))
            []
            expr))
        (-eval [_ {:keys [db]} {:keys [id]} _]
          (prom/inc! retrieve-total)
          (coll/eduction (cr/resource-mapper db) (d/execute-query db compartment-query id)))
        (-form [_]
          `(~'retrieve ~data-type ~(d/query-clauses compartment-query))))
      throw-anom)))

;; TODO: find a better solution than hard coding this case
(def ^:private specimen-patient-expr
  (reify-expr core/Expression
    (-eval [_ {:keys [db]} resource _]
      (prom/inc! retrieve-total)
      (when-let [reference (-> resource :subject :reference :value)]
        (when-let [[type id] (fsr/split-literal-ref reference)]
          (when (and (= "Patient" type) (string? id))
            (when-let [handle (d/resource-handle db "Patient" id)]
              (when-not (d/deleted? handle)
                [(cr/mk-resource db handle)]))))))
    (-form [_]
      '(retrieve (Specimen) "Patient"))))

(defn- context-expr
  "Returns an expression which, when evaluated, returns all resources of type
  `data-type` related to the resource in execution `context`."
  [context data-type]
  (case context
    "Specimen"
    (case data-type
      "Patient"
      specimen-patient-expr)
    (reify-expr core/Expression
      (-eval [_ {:keys [db]} {:keys [id]} _]
        (prom/inc! retrieve-total)
        (coll/eduction
         (cr/resource-mapper db)
         (d/list-compartment-resource-handles db context id data-type)))
      (-form [_]
        `(~'retrieve ~data-type)))))

(def ^:private resource-expr
  (reify-expr core/Expression
    (-eval [_ _ resource _]
      [resource])
    (-form [_]
      '(retrieve-resource))))

(defn- unsupported-type-ns-anom [value-type-ns]
  (ba/unsupported (format "Unsupported related context retrieve expression with result type namespace of `%s`." value-type-ns)))

(def ^:private unsupported-related-context-expr-without-type-anom
  (ba/unsupported "Unsupported related context retrieve expression without result type."))

(defn- related-context-expr-without-codes [related-context-expr data-type]
  (reify-expr core/Expression
    (-resolve-refs [_ expression-defs]
      (related-context-expr-without-codes
       (core/-resolve-refs related-context-expr expression-defs) data-type))
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

(defn- related-context-expr-with-codes [related-context-expr data-type query]
  (reify-expr core/Expression
    (-resolve-refs [_ expression-defs]
      (related-context-expr-with-codes
       (core/-resolve-refs related-context-expr expression-defs) data-type query))
    (-eval [_ {:keys [db] :as context} resource scope]
      (prom/inc! retrieve-total)
      (when-let [{:keys [id]} (core/-eval related-context-expr context resource scope)]
        (when (string? id)
          (coll/eduction
           (cr/resource-mapper db)
           (d/execute-query db query id)))))
    (-form [_]
      (list 'retrieve (core/-form related-context-expr) data-type (d/query-clauses query)))))

(defn- related-context-expr
  [node context-expr data-type code-property codes]
  (if (seq codes)
    (if-let [result-type-name (:result-type-name (meta context-expr))]
      (let [[value-type-ns context-type] (elm-util/parse-qualified-name result-type-name)]
        (if (= "http://hl7.org/fhir" value-type-ns)
          (let [clauses [(into [code-property] (map code->clause-value) codes)]]
            (if-ok [query (d/compile-compartment-query node context-type data-type clauses)]
              (related-context-expr-with-codes context-expr data-type query)
              throw-anom))
          (throw-anom (unsupported-type-ns-anom value-type-ns))))
      (throw-anom unsupported-related-context-expr-without-type-anom))
    (related-context-expr-without-codes context-expr data-type)))

(defn- unfiltered-context-expr [node data-type code-property codes]
  (if (empty? codes)
    (reify-expr core/Expression
      (-eval [_ {:keys [db]} _ _]
        (prom/inc! retrieve-total)
        (coll/eduction (cr/resource-mapper db) (d/type-list db data-type)))
      (-form [_]
        `(~'retrieve ~data-type)))
    (let [clauses [(into [code-property] (map code->clause-value) codes)]]
      (if-ok [query (d/compile-type-query node data-type clauses)]
        (reify-expr core/Expression
          (-eval [_ {:keys [db]} _ _]
            (prom/inc! retrieve-total)
            (coll/eduction (cr/resource-mapper db) (d/execute-query db query)))
          (-form [_]
            `(~'retrieve ~data-type ~(d/query-clauses query))))
        throw-anom))))

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

(defn- unsupported-dynamic-codes-expr-anom [codes-expr]
  (ba/unsupported
   (format "Unsupported dynamic codes expression `%s` in Retrieve expression."
           (core/-form codes-expr))))

(defn- unsupported-type-namespace-anom [type-ns]
  (ba/unsupported
   (format "Unsupported type namespace `%s` in Retrieve expression." type-ns)
   :type-ns type-ns))

(defn- compile-codes-expr [context codes-expr]
  (let [codes-expr (core/compile* context codes-expr)]
    (if (and (sequential? codes-expr) (every? code? codes-expr))
      codes-expr
      (throw-anom (unsupported-dynamic-codes-expr-anom codes-expr)))))

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
       (some->> codes-expr (compile-codes-expr context)))
      (throw-anom (unsupported-type-namespace-anom type-ns)))))
