(ns blaze.elm.compiler.external-data
  "11. External Data

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.elm.code :refer [code?]]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.macros :refer [reify-expr]]
   [blaze.elm.compiler.structured-values]
   [blaze.elm.resource :as cr]
   [blaze.elm.spec]
   [blaze.elm.util :as elm-util]
   [blaze.elm.value-set :as vs]
   [blaze.fhir.spec.references :as fsr]
   [blaze.util :refer [str]]
   [blaze.value-set-expander :as vse]
   [prometheus.alpha :as prom :refer [defcounter]]))

(set! *warn-on-reflection* true)

(defcounter retrieve-total
  "Number of times a retrieve expression was evaluated."
  {:namespace "blaze"
   :subsystem "cql"})

(defn- code->clause-value [{:keys [system code]}]
  (str system "|" code))

(defn- codes-clause [property codes]
  (into [property] (map code->clause-value) codes))

(defn- value-set-clause [property value-set]
  [(str property ":in") (vs/url value-set)])

(defn- retrieve-clauses
  "Returns the clauses of a retrieve of resources of `data-type` with a
  `code-property` matching `codes-expr`, which is either a value set or a list
  of codes.

  Value sets are expanded, because the database doesn't do that. Returns nil if
  the value set is empty, so that no resource can match. Throws an anomaly if
  the value set can't be expanded."
  [{:keys [value-set-expander]} data-type code-property codes-expr]
  (if (vs/value-set? codes-expr)
    (ac/join (vse/expand-clauses value-set-expander data-type
                                 [(value-set-clause code-property codes-expr)]))
    [(codes-clause code-property codes-expr)]))

(defn- form-clauses
  "Returns the clauses of `query`, compiled from `clauses`, as they appear in
  forms.

  The clauses hold the codes of expanded value sets, so that forms, which are
  used as keys of the expression cache, change if the expansion of a value set
  changes. Otherwise Bloom filters created with an outdated expansion would be
  used. For display, the value sets are shown unexpanded."
  [clauses query]
  (let [query-clauses (d/query-clauses query)]
    (cond-> query-clauses
      (vse/expanded? clauses)
      (core/with-display-form (vse/original-clauses clauses query-clauses)))))

(defn- compartment-query-expr
  [node eval-context data-type clauses]
  (let [type-query (ba/throw-when (d/compile-type-query node data-type clauses))
        compartment-query (ba/throw-when (d/compile-compartment-query node eval-context
                                                                      data-type clauses))]
    (reify-expr core/Expression
      (-optimize [expr db]
       ;; if there is no resource, regardless of the individual patient,
       ;; available, just return an empty list for further optimizations
        (if (coll/empty? (ba/throw-when (d/execute-query db type-query)))
          []
          expr))
      (-eval [_ {:keys [db]} {:keys [id]} _]
        (prom/inc! retrieve-total)
        (coll/eduction (cr/resource-mapper db) (ba/throw-when (d/execute-query db compartment-query id))))
      (-form [_]
        `(~'retrieve ~data-type ~(form-clauses clauses compartment-query))))))

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
  [node context data-type]
  (case context
    "Specimen"
    (case data-type
      "Patient"
      specimen-patient-expr)
    (let [compartment-query (ba/throw-when (d/compile-compartment-query node context data-type))]
      (reify-expr core/Expression
        (-eval [_ {:keys [db]} {:keys [id]} _]
          (prom/inc! retrieve-total)
          (coll/eduction (cr/resource-mapper db) (ba/throw-when (d/execute-query db compartment-query id))))
        (-form [_]
          `(~'retrieve ~data-type))))))

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

(defn- related-context-expr-without-codes [node related-context-expr data-type]
  (reify-expr core/Expression
    (-resolve-refs [_ expression-defs]
      (related-context-expr-without-codes
       node (core/-resolve-refs related-context-expr expression-defs) data-type))
    (-eval [_ context resource scope]
      (prom/inc! retrieve-total)
      (when-let [context-resource (core/-eval related-context-expr context resource scope)]
        (core/-eval
         (context-expr node (-> context-resource :fhir/type name) data-type)
         context
         context-resource
         scope)))
    (-form [_]
      (list 'retrieve (core/-form related-context-expr) data-type))))

(defn- related-context-expr-with-codes [related-context-expr data-type clauses query]
  (reify-expr core/Expression
    (-resolve-refs [_ expression-defs]
      (related-context-expr-with-codes
       (core/-resolve-refs related-context-expr expression-defs) data-type
       clauses query))
    (-eval [_ {:keys [db] :as context} resource scope]
      (prom/inc! retrieve-total)
      (when-let [{:keys [id]} (core/-eval related-context-expr context resource scope)]
        (when (string? id)
          (coll/eduction
           (cr/resource-mapper db)
           (ba/throw-when (d/execute-query db query id))))))
    (-form [_]
      (list 'retrieve (core/-form related-context-expr) data-type (form-clauses clauses query)))))

(defn- if-clauses
  "Returns the result of calling `f` with `clauses` or an empty list if
  `clauses` is nil, because no resource can match."
  [clauses f]
  (if clauses (f clauses) []))

(defn- related-context-expr
  [{:keys [node] :as context} context-expr data-type code-property codes-expr]
  (cond
    (or (vs/value-set? codes-expr) (seq codes-expr))
    (if-let [result-type-name (:result-type-name (meta context-expr))]
      (let [[value-type-ns context-type] (elm-util/parse-qualified-name result-type-name)]
        (if (= "http://hl7.org/fhir" value-type-ns)
          (if-clauses
           (retrieve-clauses context data-type code-property codes-expr)
           (fn [clauses]
             (let [query (ba/throw-when (d/compile-compartment-query node context-type data-type clauses))]
               (related-context-expr-with-codes context-expr data-type clauses query))))
          (ba/throw-anom (unsupported-type-ns-anom value-type-ns))))
      (ba/throw-anom unsupported-related-context-expr-without-type-anom))

    :else
    (related-context-expr-without-codes node context-expr data-type)))

(defn- type-query-expr [node data-type clauses]
  (let [query (ba/throw-when (d/compile-type-query node data-type clauses))]
    (reify-expr core/Expression
      (-eval [_ {:keys [db]} _ _]
        (prom/inc! retrieve-total)
        (coll/eduction (cr/resource-mapper db) (ba/throw-when (d/execute-query db query))))
      (-form [_]
        `(~'retrieve ~data-type ~(form-clauses clauses query))))))

(defn- unfiltered-context-expr [{:keys [node] :as context} data-type code-property codes-expr]
  (cond
    (vs/value-set? codes-expr)
    (if-clauses
     (retrieve-clauses context data-type code-property codes-expr)
     (partial type-query-expr node data-type))

    (empty? codes-expr)
    (reify-expr core/Expression
      (-eval [_ {:keys [db]} _ _]
        (prom/inc! retrieve-total)
        (coll/eduction (cr/resource-mapper db) (d/type-list db data-type)))
      (-form [_]
        `(~'retrieve ~data-type)))

    :else
    (type-query-expr node data-type [(codes-clause code-property codes-expr)])))

(defn- expr* [{:keys [node eval-context] :as context} data-type code-property codes-expr]
  (if (and (not (vs/value-set? codes-expr)) (empty? codes-expr))
    (if (= data-type eval-context)
      resource-expr
      (context-expr node eval-context data-type))
    (if-clauses
     (retrieve-clauses context data-type code-property codes-expr)
     (partial compartment-query-expr node eval-context data-type))))

;; 11.1. Retrieve
(defn- expr
  [{:keys [eval-context] :as context} context-expr data-type code-property codes-expr]
  (cond
    context-expr
    (related-context-expr context context-expr data-type code-property codes-expr)

    (= "Unfiltered" eval-context)
    (unfiltered-context-expr context data-type code-property codes-expr)

    :else
    (expr* context data-type code-property codes-expr)))

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
    (if (or (and (sequential? codes-expr) (every? code? codes-expr))
            (vs/value-set? codes-expr))
      codes-expr
      (ba/throw-anom (unsupported-dynamic-codes-expr-anom codes-expr)))))

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
      (ba/throw-anom (unsupported-type-namespace-anom type-ns)))))
