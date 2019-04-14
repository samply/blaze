(ns life-fhir-store.elm.compiler
  "Compiles ELM expressions to expressions defined by the `Expression`
  protocol in this namespace.

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html.

  Regarding time zones:
    We use date and time values with and without time zone information here.
    Every local (without time zone) date or time is meant relative to the time
    zone of the :now timestamp in the evaluation context."
  (:require
    [camel-snake-kebab.core :refer [->kebab-case-string]]
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [life-fhir-store.datomic.cql :as cql]
    [life-fhir-store.datomic.time :as time]
    [life-fhir-store.datomic.quantity :as quantity]
    [life-fhir-store.elm.date-time :as date-time]
    [life-fhir-store.elm.decimal :as decimal]
    [life-fhir-store.elm.deps-infer :refer [infer-library-deps]]
    [life-fhir-store.elm.equiv-relationships :refer [find-equiv-rels-library]]
    [life-fhir-store.elm.integer]
    [life-fhir-store.elm.interval :refer [interval]]
    [life-fhir-store.elm.nil]
    [life-fhir-store.elm.normalizer :refer [normalize-library]]
    [life-fhir-store.elm.protocols :as p]
    [life-fhir-store.elm.quantity :refer [parse-quantity]]
    [life-fhir-store.elm.spec]
    [life-fhir-store.elm.type-infer :refer [infer-library-types]]
    [life-fhir-store.elm.util :as elm-util]
    [life-fhir-store.util :as u])
  (:import
    [java.time LocalDate LocalDateTime LocalTime OffsetDateTime Year YearMonth
               ZoneOffset]
    [java.time.temporal ChronoUnit Temporal]
    [java.math RoundingMode]
    [javax.measure Quantity])
  (:refer-clojure :exclude [compile]))


(defprotocol Expression
  (-eval [this context scope])
  (-hash [this]))


(extend-protocol Expression
  nil
  (-eval [this _ _]
    this)
  (-hash [this]
    this)

  Object
  (-eval [this _ _]
    this)
  (-hash [this]
    this))


(extend-protocol p/Equal
  Object
  (equal [x y]
    (some->> y (= x))))


(extend-protocol p/Equivalent
  Object
  (equivalent [x y]
    (= x y)))


(extend-protocol p/Greater
  Comparable
  (greater [x y]
    (some->> y (.compareTo x) (< 0))))


(extend-protocol p/GreaterOrEqual
  Comparable
  (greater-or-equal [x y]
    (some->> y (.compareTo x) (<= 0))))


(extend-protocol p/Less
  Comparable
  (less [x y]
    (some->> y (.compareTo x) (> 0))))


(extend-protocol p/LessOrEqual
  Comparable
  (less-or-equal [x y]
    (some->> y (.compareTo x) (>= 0))))


(extend-protocol p/NotEqual
  Object
  (not-equal [x y]
    (some->> y (p/equal x) not)))


(defmulti compile*
  "Compiles `expression`."
  {:arglists '([context expression])}
  (fn [_ {:keys [type]}]
    (keyword "elm.compiler.type" (->kebab-case-string type))))


(defmethod compile* :default
  [_ {:keys [type]}]
  (throw (Exception. (str "Unsupported ELM expression type: " (or type "<missing>")))))


(s/def :life/expression
  #(satisfies? Expression %))


(s/def ::now
  #(satisfies? OffsetDateTime %))


(s/def ::library-context
  (s/map-of string? some?))


(s/def ::eval-context
  (s/keys :req-un [::ds/db ::now] :opt-un [::library-context]))


(s/def ::compile-context
  (s/keys :req-un [:elm/library]))


(s/fdef compile
  :args (s/cat :context ::compile-context :expression :elm/expression)
  :ret :life/expression)

(defn compile [context expression]
  (compile* context expression))


(s/def :life/compiled-expression-def
  (s/merge :elm/expression-def (s/keys :req [:life/expression])))


(defn compile-expression-def
  "Compiles the expression of `expression-def` in `context` and assocs the
  resulting code under :life/code to the `expression-def` which is returned."
  {:arglists '([context expression-def])}
  [context {:keys [expression] :as expression-def}]
  (let [context (assoc context :eval-context (:context expression-def))]
    (try
      (assoc expression-def :life/expression (compile context expression))
      (catch Exception e
        #::anom
            {:category ::anom/fault
             :message (.getMessage e)
             :e e
             :elm/expression expression}))))


(s/fdef compile-library
  :args (s/cat :library :elm/library :opts map?)
  :ret (s/coll-of (s/or :result (s/coll-of :life/compiled-expression-def)
                        :anomaly ::anom/anomaly)))

(defn compile-library
  "Returns a collection of compiled expression defs.

  There are currently no options."
  [library opts]
  (let [library (-> library
                    normalize-library
                    find-equiv-rels-library
                    infer-library-deps
                    infer-library-types)
        context (assoc opts :library library)]
    (mapv #(compile-expression-def context %) (-> library :statements :def))))


(defmacro defunary-operator
  ([name]
   `(defunary-operator ~name ~(symbol "life-fhir-store.elm.protocols" name)))
  ([name op]
   `(defmethod compile* ~(keyword "elm.compiler.type" name)
      [context# {operand# :operand}]
      (let [operand# (compile context# operand#)]
        (reify Expression
          (-eval [_ context# scope#]
            (~op (-eval operand# context# scope#)))
          (-hash [_]
            {:type ~(keyword name)
             :operand (-hash operand#)}))))))


(defmacro defbinary-operator [name]
  `(defmethod compile* ~(keyword "elm.compiler.type" name)
     [context# {[operand-1# operand-2#] :operand}]
     (let [operand-1# (compile context# operand-1#)
           operand-2# (compile context# operand-2#)]
       (reify Expression
         (-eval [_ context# scope#]
           (~(symbol "life-fhir-store.elm.protocols" name)
             (-eval operand-1# context# scope#)
             (-eval operand-2# context# scope#)))
         (-hash [_]
           {:type ~(keyword name)
            :operands [(-hash operand-1#) (-hash operand-2#)]})))))


(defn- literal? [x]
  (or (boolean? x)
      (number? x)
      (string? x)
      (instance? Temporal x)
      (instance? Quantity x)))



;; 1. Simple Values

;; 1.1 Literal
;;
;; The Literal type defines a single scalar value. For example, the literal 5,
;; the boolean value true or the string "antithrombotic".
(defmethod compile* :elm.compiler.type/literal
  [_ {:keys [value] value-type :valueType}]
  (when value
    (let [[value-type-ns value-type-name] (elm-util/parse-qualified-name value-type)]
      (case value-type-ns
        "urn:hl7-org:elm-types:r1"
        (case value-type-name
          "Boolean" (Boolean/valueOf ^String value)
          ;; TODO: maybe we can even use integers here
          "Integer" (long (Integer/parseInt value))
          "Decimal" (decimal/from-literal value)
          "String" value
          (throw (Exception. (str value-type-name " literals are not supported"))))))))


;; 2. Structured Values

;; 2.1. Tuple
(defmethod compile* :elm.compiler.type/tuple
  [context {elements :element}]
  (let [elements
        (reduce
          (fn [r {:keys [name value]}]
            (assoc r name (compile context value)))
          {}
          elements)]
    (reify Expression
      (-eval [_ context scope]
        (reduce-kv
          (fn [r name value]
            (assoc r (keyword name) (-eval value context scope)))
          {}
          elements))
      (-hash [_]
        {:type :tuple
         :elements
         (reduce-kv
           (fn [r name value]
             (assoc r name (-hash value)))
           {}
           elements)}))))


;; 2.3. Property
;;
;; The Property operator returns the value of the property on `source` specified
;; by the `path` attribute.
;;
;; If the result of evaluating source is null, the result is null.
;;
;; If a scope is specified, the name is used to resolve the scope in which the
;; path will be resolved. Scopes can be named by operators such as Filter and
;; ForEach.

(defn- source-property-expression
  "Creates an Expression which returns the property of `attr-kw` from `source`."
  [attr-kw source]
  (reify Expression
    (-eval [_ context scope]
      (attr-kw (-eval source context scope)))
    (-hash [_]
      {:type :property
       :attr-kw attr-kw
       :source (-hash source)})))


(defn- time-source-property-expression
  "Creates an Expression which returns the property of `attr-kw` of type date,
  dateTime or time from `source`. Such properties have to be deserialized from
  byte array."
  [attr-kw source]
  (reify Expression
    (-eval [_ context scope]
      (time/read (attr-kw (-eval source context scope))))
    (-hash [_]
      {:type :property
       :attr-kw attr-kw
       :source (-hash source)})))


(defn- quantity-source-property-expression
  "Creates an Expression which returns the property of `attr-kw` of type
  Quantity from `source`. Such properties have to be deserialized from byte
  array."
  [attr-kw source]
  (reify Expression
    (-eval [_ context scope]
      (quantity/read (attr-kw (-eval source context scope))))
    (-hash [_]
      {:type :property
       :attr-kw attr-kw
       :source (-hash source)})))


(defn- scope-property-expression
  "Creates an Expression which returns the property of `attr-kw` from either
  a direct supplied entity or an entity from query context by `scope`."
  ([attr-kw]
   (reify Expression
     (-eval [_ _ entity]
       (attr-kw entity))
     (-hash [_]
       {:type :property
        :attr-kw attr-kw})))
  ([attr-kw scope]
   (reify Expression
     (-eval [_ _ query-context]
       (attr-kw (get query-context scope)))
     (-hash [_]
       {:type :property
        :attr-kw attr-kw
        :scope scope}))))


(defn- time-scope-property-expression
  "Creates an Expression which returns a property of `attr-kw` of type date,
  dateTime or time from either a direct supplied entity or an entity from query
  context by `scope`. Such properties have to be deserialized from byte array."
  ([attr-kw]
   (reify Expression
     (-eval [_ _ entity]
       (time/read (attr-kw entity)))
     (-hash [_]
       {:type :property
        :attr-kw attr-kw})))
  ([attr-kw scope]
   (reify Expression
     (-eval [_ _ query-context]
       (time/read (attr-kw (get query-context scope))))
     (-hash [_]
       {:type :property
        :attr-kw attr-kw
        :scope scope}))))


(defn- quantity-scope-property-expression
  "Creates an Expression which returns a property of `attr-kw` of type Quantity
  from either a direct supplied entity or an entity from query context by
  `scope`. Such properties have to be deserialized from byte array."
  ([attr-kw]
   (reify Expression
     (-eval [_ _ entity]
       (quantity/read (attr-kw entity)))
     (-hash [_]
       {:type :property
        :attr-kw attr-kw})))
  ([attr-kw scope]
   (reify Expression
     (-eval [_ _ query-context]
       (quantity/read (attr-kw (get query-context scope))))
     (-hash [_]
       {:type :property
        :attr-kw attr-kw
        :scope scope}))))


(defn- tuple-type-specifier?
  {:arglists '([type-specifier])}
  [{:keys [type]}]
  (= "TupleTypeSpecifier" type))


(defn- choice-type-specifier?
  {:arglists '([type-specifier])}
  [{:keys [type]}]
  (sequential? type))


(defn contains-choice-type?
  {:arglists '([choice-type-specifier type-name])}
  [{:keys [type]} type-name]
  (when type-name
    (some (fn [{:keys [name]}] (= type-name name)) type)))


(defn- extract-local-fhir-name [type-name]
  (let [[ns name] (elm-util/parse-qualified-name type-name)]
    (if (= "http://hl7.org/fhir" ns)
      name
      (throw (Exception. (str "Unsupported type namespace `"
                              ns "` in `Property` expression."))))))


(defn attr-kw
  {:arglists '([property-expression])}
  [{:life/keys [source-type as-type-name] :keys [path source]
    result-type-specifier :resultTypeSpecifier :as expr}]
  (cond
    source-type
    (let [[source-type-ns source-type-name] (elm-util/parse-qualified-name source-type)]
      (if (= "http://hl7.org/fhir" source-type-ns)
        (if (choice-type-specifier? result-type-specifier)
          (if (contains-choice-type? result-type-specifier as-type-name)
            (keyword source-type-name (str path (u/title-case (extract-local-fhir-name as-type-name))))
            (throw (ex-info (str "Ambiguous choice type on property `"
                                 source-type-name "/" path "`.") expr)))
          (keyword source-type-name path))
        (throw (ex-info (str "Unsupported source type namespace `"
                             source-type-ns "` in property expression.") expr))))
    source
    (let [{type-specifier :resultTypeSpecifier type-name :resultTypeName} source]
      (cond
        (tuple-type-specifier? type-specifier) (keyword path)
        type-name (attr-kw (assoc expr :life/source-type type-name))
        :else
        (throw (ex-info "Unable to determine attr-kw on property expression." expr))))
    :else
    (throw (ex-info "Unable to determine attr-kw on property expression." expr))))


(defn- property-result-type
  {:arglists '([property-expression])}
  [{result-type-name :resultTypeName
    result-type-specifier :resultTypeSpecifier
    :life/keys [as-type-name]
    :as expr}]
  (cond
    result-type-name
    (elm-util/parse-qualified-name result-type-name)

    (choice-type-specifier? result-type-specifier)
    (if (contains-choice-type? result-type-specifier as-type-name)
      (elm-util/parse-qualified-name as-type-name)
      (throw (ex-info "Ambiguous choice type on property." expr)))

    :else
    (throw (ex-info "Undetermined result type on property." expr))))


(defmethod compile* :elm.compiler.type/property
  [{:life/keys [single-query-scope] :as context}
   {:keys [source scope] :as expression}]
  (let [attr-kw (attr-kw expression)
        [result-type-ns result-type-name] (property-result-type expression)
        source (some->> source (compile context))]
    (cond
      ;; We evaluate the `source` to retrieve the entity.
      source
      (let [property-expression-fn
            (case result-type-ns
              "http://hl7.org/fhir"
              (case result-type-name
                ("date" "dateTime" "time")
                time-source-property-expression
                "Quantity"
                quantity-source-property-expression
                source-property-expression)
              source-property-expression)]
        (property-expression-fn attr-kw source))

      ;; We use the `scope` to retrieve the entity from `query-context`.
      scope
      (let [property-expression-fn
            (case result-type-ns
              "http://hl7.org/fhir"
              (case result-type-name
                ("date" "dateTime" "time")
                time-scope-property-expression
                "Quantity"
                quantity-scope-property-expression
                scope-property-expression)
              scope-property-expression)]
        (if (= single-query-scope scope)
          (property-expression-fn attr-kw)
          (property-expression-fn attr-kw scope))))))



;; 3. Clinical Values

;; 3.3. CodeRef
;;
;; The CodeRef expression allows a previously defined code to be referenced
;; within an expression.
(defn- get-code-def
  "Returns the code-def with `name` from `library` or nil if not found."
  {:arglists '([library name])}
  [{{code-defs :def} :codes} name]
  (some #(when (= name (:name %)) %) code-defs))

(defmethod compile* :elm.compiler.type/code-ref
  [{:keys [library] :as context} {:keys [name]}]
  ;; TODO: look into other libraries (:libraryName)
  (when-let [{code-system-ref :codeSystem code :id} (get-code-def library name)]
    (if code-system-ref
      (when-let [{system :id} (compile context (assoc code-system-ref :type "CodeSystemRef"))]
        ;; TODO: version
        (reify Expression
          (-eval [_ {:keys [db]} _]
            (cql/find-coding db system code))
          (-hash [_]
            {:type :code-ref
             :system system
             :code code})))
      `(fn ~'[{:keys [db]}]
         (d/q ~'[:find ?coding . :in $ ?code :where [?coding :Coding/code ?code]]
              ~'_db ~code)))))

(defn- get-code-system-def
  "Returns the code-system-def with `name` from `library` or nil if not found."
  {:arglists '([library name])}
  [{{code-system-defs :def} :codeSystems} name]
  (some #(when (= name (:name %)) %) code-system-defs))


;; 3.5. CodeSystemRef
(defmethod compile* :elm.compiler.type/code-system-ref
  [{:keys [library]} {:keys [name]}]
  ;; TODO: look into other libraries (:libraryName)
  (get-code-system-def library name))


;; 3.9. Quantity
(defmethod compile* :elm.compiler.type/quantity
  [_ {:keys [value unit]}]
  (if unit
    (case unit
      ("year" "years") (date-time/period value 0 0)
      ("month" "months") (date-time/period 0 value 0)
      ("week" "weeks") (date-time/period 0 0 (* value 7 24 3600))
      ("day" "days") (date-time/period 0 0 (* value 24 3600))
      ("hour" "hours") (date-time/period 0 0 (* value 3600))
      ("minute" "minutes") (date-time/period 0 0 (* value 60))
      ("second" "seconds") (date-time/period 0 0 value)
      ;; TODO: wrong
      ("millisecond" "milliseconds") (date-time/period 0 0 value)
      (parse-quantity value unit))
    value))



;; 9. Reusing Logic

;; 9.2. ExpressionRef
;;
;; The ExpressionRef type defines an expression that references a previously
;; defined NamedExpression. The result of evaluating an ExpressionReference is
;; the result of evaluating the referenced NamedExpression.
(defmethod compile* :elm.compiler.type/expression-ref
  [{:keys [eval-context]} {:keys [name] def-eval-context :life/eval-context}]
  ;; TODO: look into other libraries (:libraryName)
  (when name
    (if (and (= "Population" eval-context) (= "Patient" def-eval-context))
      ;; The referenced expression has Patient context but we are in the
      ;; Population context. So we map the referenced expression over all
      ;; patients.
      (reify Expression
        (-eval [_ {:keys [db library-context] :as context} scope]
          (mapv #(-eval (get library-context name) (assoc context :patient %) scope)
                (cql/list-resource db "Patient")))
        (-hash [_]
          ;; TODO: the expression does something different here. should it have a different hash?
          {:type :expression-ref
           :name name}))

      (reify Expression
        (-eval [_ {:keys [library-context]} _]
          (or (get library-context name)
              (throw (Exception. (str "Expression reference `" name "` not found.")))))
        (-hash [_]
          {:type :expression-ref
           :name name})))))


;; 9.4. FunctionRef
(defmethod compile* :elm.compiler.type/function-ref
  [context {:keys [name] operands :operand}]
  ;; TODO: look into other libraries (:libraryName)
  (let [operands (mapv #(compile context %) operands)]
    (case name
      "ToQuantity"
      (let [operand (first operands)]
        (reify Expression
          (-eval [_ context scope]
            (-eval operand context scope))
          (-hash [_]
            {:type :function-ref
             :name name
             :operand (-hash operand)})))

      "ToDate"
      (let [operand (first operands)]
        (reify Expression
          (-eval [_ {:keys [now] :as context} scope]
            (p/to-date (-eval operand context scope) now))
          (-hash [_]
            {:type :function-ref
             :name name
             :operand (-hash operand)})))

      "ToDateTime"
      (let [operand (first operands)]
        (reify Expression
          (-eval [_ {:keys [now] :as context} scope]
            (p/to-date-time (-eval operand context scope) now))
          (-hash [_]
            {:type :function-ref
             :name name
             :operand (-hash operand)})))

      (throw (Exception. (str "Unsupported function `" name "` in `FunctionRef` expression."))))))



;; 10. Queries
(declare compile-with-equiv-clause)

;; 10.1. Query
;;
;; The Query operator represents a clause-based query. The result of the query
;; is determined by the type of sources included, as well as the clauses used in
;; the query.
(defn- with-xform [with-clause]
  (fn create-with-xform [context]
    (let [with-clause (with-clause context)]
      (filter #(with-clause context %)))))


(defn- where-xform [where-expr]
  (fn [context]
    (filter #(-eval where-expr context %))))


(defn- return-xform [return-expr]
  (fn [context]
    (map #(-eval return-expr context %))))


(defn- xform [with-xforms where-xform return-xform]
  (fn [context]
    (apply
      comp
      (cond-> []
        (some? where-xform) (conj (where-xform context))
        (seq with-xforms) (into (map #(% context) with-xforms))
        (some? return-xform) (conj (return-xform context))))))


(defmethod compile* :elm.compiler.type/query
  [context
   {sources :source
    relationships :relationship
    :keys [where]
    {return :expression} :return}]
  (if (= 1 (count sources))
    (let [{:keys [expression alias]} (first sources)
          context (assoc context :life/single-query-scope alias)
          with-equiv-clauses (filter #(= "WithEquiv" (:type %)) relationships)
          with-equiv-clauses (map #(compile-with-equiv-clause context %) with-equiv-clauses)
          with-xforms (map with-xform with-equiv-clauses)
          where-xform (some->> where (compile context) where-xform)
          return-xform (some->> return (compile context) return-xform)
          xform (xform with-xforms where-xform return-xform)
          source (compile context expression)]
      (reify Expression
        (-eval [_ context _]
          (into #{} (xform context) (-eval source context nil)))
        (-hash [_]
          (cond->
            {:type :query
             :source (-hash source)}
            (some? where) (assoc :where (-hash where))
            (seq with-equiv-clauses) (assoc :with (-hash with-equiv-clauses))
            (some? return) (assoc :return (-hash return))))))
    (throw (Exception. (str "Unsupported number of " (count sources) " sources in query.")))))


;; 10.3. AliasRef
;;
;; The AliasRef expression allows for the reference of a specific source within
;; the context of a query.
(defmethod compile* :elm.compiler.type/alias-ref
  [{:life/keys [single-query-scope]} {:keys [name]}]
  (if (= single-query-scope name)
    (reify Expression
      (-eval [_ _ entity]
        entity)
      (-hash [_]
        {:type :alias-ref}))
    (reify Expression
      (-eval [_ _ query-context]
        (get query-context name))
      (-hash [_]
        {:type :alias-ref
         :name name}))))


;; 10.12. With
;;
;; The With clause restricts the elements of a given source to only those
;; elements that have elements in the related source that satisfy the suchThat
;; condition. This operation is known as a semi-join in database languages.
;; condition. This operation is known as a semi-join in database languages.
(defn- find-operand-with-alias
  "Finds the operand in `expression` that accesses entities with `alias`."
  [operands alias]
  (some #(when (= (:life/scopes %) #{alias}) %) operands))


(s/fdef compile-with-equiv-clause
  :args (s/cat :context ::compile-context :with-equiv-clause :elm.query.life/with-equiv)
  :ret fn?)

(defn compile-with-equiv-clause
  "We use the terms `lhs` and `rhs` for left-hand-side and right-hand-side of
  the semi-join here."
  {:arglists '([context with-equiv-clause])}
  [context {:keys [alias] rhs :expression equiv-operands :equivOperand
            such-that :suchThat}]
  (if-let [single-query-scope (:life/single-query-scope context)]
    (if-let [rhs-operand (find-operand-with-alias equiv-operands alias)]
      (if-let [lhs-operand (find-operand-with-alias equiv-operands single-query-scope)]
        (let [rhs (compile context rhs)
              rhs-operand (compile (assoc context :life/single-query-scope alias) rhs-operand)
              lhs-operand (compile context lhs-operand)
              such-that (some->> such-that (compile (dissoc context :life/single-query-scope)))]
          (fn create-with-clause [eval-context]
            (let [rhs (-eval rhs eval-context nil)
                  indexer #(-eval rhs-operand eval-context %)]
              (if (some? such-that)
                (let [index (group-by indexer rhs)]
                  (fn eval-with-clause [eval-context lhs-entity]
                    (when-let [rhs-entities (some->> (-eval lhs-operand eval-context lhs-entity) (get index))]
                      (some #(-eval such-that eval-context {single-query-scope lhs-entity alias %}) rhs-entities))))
                (let [index (into #{} (map indexer) rhs)]
                  (fn eval-with-clause [eval-context lhs-entity]
                    (some->> (-eval lhs-operand eval-context lhs-entity) (contains? index))))))))
        (throw (Exception. "Unsupported call without left-hand-side operand.")))
      (throw (Exception. "Unsupported call without right-hand-side operand.")))
    (throw (Exception. "Unsupported call without single query scope."))))


;; TODO 10.13. Without



;; 11. External Data

;; 11.1. Retrieve
;;
;; Implementation Note:
;;
;; The compiler generates a function with takes a context map with a required
;; :db and an optional :patient-id. The :db is the database from which the data
;; will be retrieved.
;;
;; The :patient-id can be optionally populated with a patient identifier to
;; retrieve only data from that patient. In case :patient-id is `nil`, data from
;; all patients will be retrieved.
;;
;; The :patient-id will be set if the evaluation context is `"Patient"`. It'll
;; be `nil` if the evaluation context is `"Population"`.
(defmethod compile* :elm.compiler.type/retrieve
  [{:keys [eval-context] :as context}
   {:keys [codes] code-property-name :codeProperty data-type :dataType
    :or {code-property-name "code"}}]
  (let [patient-eval-context? (= "Patient" eval-context)
        [_ data-type-name] (elm-util/parse-qualified-name data-type)]
    (if-let [codings (some->> codes (compile context))]
      (if patient-eval-context?
        (reify Expression
          (-eval [_ {:keys [patient] :as context} _]
            (cql/list-patient-resource-by-code
              patient data-type-name code-property-name (-eval codings context nil)))
          (-hash [_]
            {:type :retrieve
             :context :patient
             :data-type-name data-type-name
             :code-property-name code-property-name
             :codings (-hash codings)}))
        (reify Expression
          (-eval [_ {:keys [db] :as context} _]
            (cql/list-resource-by-code
              db data-type-name code-property-name (-eval codings context nil)))
          (-hash [_]
            {:type :retrieve
             :context :db
             :data-type-name data-type-name
             :code-property-name code-property-name
             :codings (-hash codings)})))

      (if patient-eval-context?
        (if (= "Patient" data-type-name)
          (reify Expression
            (-eval [_ {:keys [patient]} _]
              [patient])
            (-hash [_]
              {:type :retrieve
               :context :patient}))

          ;; TODO: are there resources without a subject property?
          (let [reverse-subject-kw (keyword data-type-name "_subject")]
            (reify Expression
              (-eval [_ {:keys [patient]} _]
                (reverse-subject-kw patient))
              (-hash [_]
                {:type :retrieve
                 :context :patient
                 :reverse-subject-kw reverse-subject-kw}))))

        (reify Expression
          (-eval [_ {:keys [db]} _]
            (cql/list-resource db data-type-name))
          (-hash [_]
            {:type :retrieve
             :context :db
             :data-type-name data-type-name}))))))



;; 12. Comparison Operators

;; 12.1. Equal
(defbinary-operator "equal")


;; 12.2. Equivalent
(defbinary-operator "equivalent")


;; 12.3. Greater
(defbinary-operator "greater")


;; 12.4. GreaterOrEqual
(defbinary-operator "greater-or-equal")


;; 12.5. Less
(defbinary-operator "less")


;; 12.6. LessOrEqual
(defbinary-operator "less-or-equal")


;; 12.7. NotEqual
(defbinary-operator "not-equal")



;; 13. Logical Operators

;; 13.1. And
(defmethod compile* :elm.compiler.type/and
  [context {[operand-1 operand-2] :operand}]
  (let [operand-1 (compile context operand-1)
        operand-2 (compile context operand-2)]
    (reify Expression
      (-eval [_ context scope]
        (let [operand-1 (-eval operand-1 context scope)]
          (if (false? operand-1)
            false
            (let [operand-2 (-eval operand-2 context scope)]
              (cond
                (false? operand-2) false
                (and (true? operand-1) (true? operand-2)) true)))))
      (-hash [_]
        {:type :and
         :operands [(-hash operand-1) (-hash operand-2)]}))))


;; 13.2 Implies
(defmethod compile* :elm.compiler.type/implies
  [_ _]
  (throw (Exception. "Unsupported Implies expression. Please normalize the ELM tree before compiling.")))


;; 13.3 Not
(defmethod compile* :elm.compiler.type/not
  [context {operand :operand}]
  (let [operand (compile context operand)]
    (reify Expression
      (-eval [_ context scope]
        (let [operand (-eval operand context scope)]
          (cond
            (true? operand) false
            (false? operand) true)))
      (-hash [_]
        {:type :not
         :operand (-hash operand)}))))


;; 13.4. Or
(defmethod compile* :elm.compiler.type/or
  [context {[operand-1 operand-2] :operand}]
  (let [operand-1 (compile context operand-1)
        operand-2 (compile context operand-2)]
    (reify Expression
      (-eval [_ context scope]
        (let [operand-1 (-eval operand-1 context scope)]
          (if (true? operand-1)
            true
            (let [operand-2 (-eval operand-2 context scope)]
              (cond
                (true? operand-2) true
                (and (false? operand-1) (false? operand-2)) false)))))
      (-hash [_]
        {:type :and
         :operands [(-hash operand-1) (-hash operand-2)]}))))


;; 13.5 Xor
(defmethod compile* :elm.compiler.type/xor
  [_ _]
  (throw (Exception. "Unsupported Xor expression. Please normalize the ELM tree before compiling.")))



;; 14. Nullological Operators

;; 14.1. Null
(defmethod compile* :elm.compiler.type/null
  [_ _])


;; 14.2. Coalesce
;;
;; The Coalesce operator returns the first non-null result in a list of
;; arguments. If all arguments evaluate to null, the result is null.
(defmethod compile* :elm.compiler.type/coalesce
  [context {operands :operand}]
  (let [operands (mapv #(compile context %) operands)]
    (reify Expression
      (-eval [_ context scope]
        (reduce
          (fn [_ x]
            (when (some? (-eval x context scope))
              (reduced x)))
          nil
          operands))
      (-hash [_]
        {:type :and
         :operands (mapv -hash operands)}))))


;; 14.3. IsFalse
(defunary-operator "is-false" false?)


;; 14.4. IsNull
(defunary-operator "is-null" nil?)


;; 14.5. IsTrue
(defunary-operator "is-true" true?)



;; 15. Conditional Operators

;; 15.2. If
(defmethod compile* :elm.compiler.type/if
  [context {:keys [condition then else]}]
  (let [condition (compile context condition)
        then (compile context then)
        else (compile context else)]
    (reify Expression
      (-eval [_ context scope]
        (if (-eval condition context scope)
          (-eval then context scope)
          (-eval else context scope)))
      (-hash [_]
        {:type :if
         :condition (-hash condition)
         :then (-hash then)
         :else (-hash else)}))))



;; 16. Arithmetic Operators

;; 16.1. Abs
(defunary-operator "abs")


;; 16.2. Add
(defbinary-operator "add")


;; 16.3. Ceiling
(defunary-operator "ceiling")


;; 16.4. Divide
(defbinary-operator "divide")


;; 16.5. Exp
(defunary-operator "exp")


;; 16.6. Floor
(defunary-operator "floor")


;; 16.7. Log
(defbinary-operator "log")


;; 16.8. Ln
(defunary-operator "ln")


;; 16.9. MaxValue
(defmethod compile* :elm.compiler.type/max-value
  [_ {type :valueType}]
  (let [[ns name] (elm-util/parse-qualified-name type)]
    (case ns
      "urn:hl7-org:elm-types:r1"
      (case name
        "Integer" Integer/MAX_VALUE
        "Decimal" decimal/max
        "Date" date-time/max-date
        "DateTime" date-time/max-date-time
        "Time" date-time/max-time
        (throw (ex-info (str "Unsupported type `" name "` for MaxValue.")
                        {:type type})))
      (throw (ex-info (str "Unknown type namespace `" ns "`.")
                      {:type type})))))


;; 16.10. MinValue
(defmethod compile* :elm.compiler.type/min-value
  [_ {type :valueType}]
  (let [[ns name] (elm-util/parse-qualified-name type)]
    (case ns
      "urn:hl7-org:elm-types:r1"
      (case name
        "Integer" Integer/MIN_VALUE
        "Decimal" decimal/min
        "Date" date-time/min-date
        "DateTime" date-time/min-date-time
        "Time" LocalTime/MIN
        (throw (ex-info (str "Unsupported type `" name "` for MinValue.")
                        {:type type})))
      (throw (ex-info (str "Unknown type namespace `" ns "`.")
                      {:type type})))))


;; 16.11. Modulo
(defbinary-operator "modulo")


;; 16.12. Multiply
(defbinary-operator "multiply")


;; 16.13. Negate
(defunary-operator "negate")


;; 16.14. Power
(defbinary-operator "power")


;; 16.15. Predecessor
(defunary-operator "predecessor")


;; 16.16. Round
(defmethod compile* :elm.compiler.type/round
  [context {:keys [operand precision]}]
  (let [operand (compile context operand)
        precision (some->> precision (compile context))]
    (cond
      (or (nil? precision) (and (number? precision) (zero? precision)))
      (reify Expression
        (-eval [_ context scope]
          (p/round (-eval operand context scope) 0))
        (-hash [_]
          {:type :if
           :operand (-hash operand)}))

      (number? precision)
      (reify Expression
        (-eval [_ context scope]
          (p/round (-eval operand context scope) precision))
        (-hash [_]
          {:type :if
           :operand (-hash operand)
           :precision (-hash precision)}))

      :else
      (reify Expression
        (-eval [_ context scope]
          (p/round (-eval operand context scope)
                   (-eval precision context scope)))
        (-hash [_]
          {:type :if
           :operand (-hash operand)
           :precision (-hash precision)})))))


;; 16.17. Subtract
(defbinary-operator "subtract")


;; 16.18. Successor
(defunary-operator "successor")


;; 16.19. Truncate
(defunary-operator "truncate")


;; 16.20. TruncatedDivide
(defbinary-operator "truncated-divide")



;; 18. Date and Time Operators

(defn- to-year [year]
  (when-not (< 0 year 10000)
    (throw (Exception. (str "Year `" year "` out of range."))))
  (Year/of year))


(defn- to-month [year month]
  (when-not (< 0 year 10000)
    (throw (Exception. (str "Year `" year "` out of range."))))
  (YearMonth/of ^long year ^long month))


(defn- to-day [year month day]
  (when-not (< 0 year 10000)
    (throw (Exception. (str "Year `" year "` out of range."))))
  (LocalDate/of ^long year ^long month ^long day))


(defn- to-local-date-time
  [year month day hour minute second millisecond]
  (when-not (< 0 year 10000)
    (throw (Exception. (str "Year `" year "` out of range."))))
  (LocalDateTime/of ^long year ^long month ^long day
                    ^long hour ^long minute ^long second
                    ^long (* 1000000 millisecond)))


(defn- to-local-date-time-with-offset
  "Creates a DateTime with a local date time adjusted for the offset of the
  evaluation request."
  [now year month day hour minute second millisecond timezone-offset]
  (when-not (< 0 year 10000)
    (throw (Exception. (str "Year `" year "` out of range."))))
  (-> (OffsetDateTime/of ^long year ^long month ^long day ^long hour
                         ^long minute ^long second (* 1000000 millisecond)
                         (ZoneOffset/ofTotalSeconds (* timezone-offset 3600)))
      (.withOffsetSameInstant (.getOffset ^OffsetDateTime now))
      (.toLocalDateTime)))


;; 18.6. Date
;;
;; The Date operator constructs a date value from the given components.
;;
;; At least one component must be specified, and no component may be specified
;; at a precision below an unspecified precision. For example, month may be null,
;; but if it is, day must be null as well.
(defmethod compile* :elm.compiler.type/date
  [context {:keys [year month day]}]
  (let [year (some->> year (compile context))
        month (some->> month (compile context))
        day (some->> day (compile context))]
    (cond
      (and (int? day) (int? month) (int? year))
      (to-day year month day)

      (some? day)
      (reify Expression
        (-eval [_ context scope]
          (to-day (-eval year context scope)
                  (-eval month context scope)
                  (-eval day context scope)))
        (-hash [_]
          {:type :date
           :year (-hash year)
           :month (-hash month)
           :day (-hash day)}))

      (and (int? month) (int? year))
      (to-month year month)

      (some? month)
      (reify Expression
        (-eval [_ context scope]
          (to-month (-eval year context scope)
                    (-eval month context scope)))
        (-hash [_]
          {:type :date
           :year (-hash year)
           :month (-hash month)}))

      (int? year)
      (some-> year to-year)

      :else
      (reify Expression
        (-eval [_ context scope]
          (some-> (-eval year context scope) to-year))
        (-hash [_]
          {:type :date
           :year (-hash year)})))))


;; 18.8. DateTime
;;
;; The DateTime operator constructs a date/time value from the given components.
;;
;; At least one component other than timezoneOffset must be specified, and no
;; component may be specified at a precision below an unspecified precision.
;; For example, hour may be null, but if it is, minute, second, and millisecond
;; must all be null as well.
;;
;; If timezoneOffset is not specified, it is defaulted to the timezone offset
;; of the evaluation request.
(defmethod compile* :elm.compiler.type/date-time
  [context {:keys [year month day hour minute second millisecond timezone-offset]
            :as expression}]
  (let [year (some->> year (compile context))
        month (some->> month (compile context))
        day (some->> day (compile context))
        hour (some->> hour (compile context))
        minute (or (some->> minute (compile context)) 0)
        second (or (some->> second (compile context)) 0)
        millisecond (or (some->> millisecond (compile context)) 0)
        timezone-offset (some->> timezone-offset (compile context))]
    (cond
      (number? timezone-offset)
      (cond
        (and (int? millisecond) (int? second) (int? minute) (int? hour)
             (int? day) (int? month) (int? year))
        (reify Expression
          (-eval [_ {:keys [now]} _]
            (to-local-date-time-with-offset
              now year month day hour minute second millisecond timezone-offset))
          (-hash [_]
            {:type :date-time
             :year (-hash year)
             :month (-hash month)
             :day (-hash day)
             :hour (-hash hour)
             :minute (-hash minute)
             :second (-hash second)
             :millisecond (-hash millisecond)
             :timezone-offset (-hash timezone-offset)}))

        (some? hour)
        (reify Expression
          (-eval [_ {:keys [now] :as context} scope]
            (to-local-date-time-with-offset
              now
              (-eval year context scope)
              (-eval month context scope)
              (-eval day context scope)
              (-eval hour context scope)
              (or (-eval minute context scope) 0)
              (or (-eval second context scope) 0)
              (or (-eval millisecond context scope) 0)
              timezone-offset))
          (-hash [_]
            {:type :date-time
             :year (-hash year)
             :month (-hash month)
             :day (-hash day)
             :hour (-hash hour)
             :minute (-hash minute)
             :second (-hash second)
             :millisecond (-hash millisecond)
             :timezone-offset (-hash timezone-offset)}))

        :else
        (throw (ex-info "Need at least an hour if timezone offset is given."
                        {:expression expression})))

      (some? timezone-offset)
      (cond
        (some? hour)
        (reify Expression
          (-eval [_ {:keys [now] :as context} scope]
            (to-local-date-time-with-offset
              now
              (-eval year context scope)
              (-eval month context scope)
              (-eval day context scope)
              (-eval hour context scope)
              (or (-eval minute context scope) 0)
              (or (-eval second context scope) 0)
              (or (-eval millisecond context scope) 0)
              (-eval timezone-offset context scope)))
          (-hash [_]
            {:type :date-time
             :year (-hash year)
             :month (-hash month)
             :day (-hash day)
             :hour (-hash hour)
             :minute (-hash minute)
             :second (-hash second)
             :timezone-offset (-hash timezone-offset)}))

        :else
        (throw (ex-info "Need at least an hour if timezone offset is given."
                        {:expression expression})))

      :else
      (cond
        (and (int? millisecond) (int? second) (int? minute) (int? hour)
             (int? day) (int? month) (int? year))
        (to-local-date-time year month day hour minute second millisecond)

        (some? hour)
        (reify Expression
          (-eval [_ context scope]
            (to-local-date-time
              (-eval year context scope)
              (-eval month context scope)
              (-eval day context scope)
              (-eval hour context scope)
              (or (-eval minute context scope) 0)
              (or (-eval second context scope) 0)
              (or (-eval millisecond context scope) 0)))
          (-hash [_]
            {:type :date-time
             :year (-hash year)
             :month (-hash month)
             :day (-hash day)
             :hour (-hash hour)
             :minute (-hash minute)
             :second (-hash second)
             :millisecond (-hash millisecond)}))

        (and (int? day) (int? month) (int? year))
        (to-day year month day)

        (some? day)
        (reify Expression
          (-eval [_ context scope]
            (to-day (-eval year context scope)
                    (-eval month context scope)
                    (-eval day context scope)))
          (-hash [_]
            {:type :date
             :year (-hash year)
             :month (-hash month)
             :day (-hash day)}))

        (and (int? month) (int? year))
        (to-month year month)

        (some? month)
        (reify Expression
          (-eval [_ context scope]
            (to-month (-eval year context scope)
                      (-eval month context scope)))
          (-hash [_]
            {:type :date
             :year (-hash year)
             :month (-hash month)}))

        (int? year)
        (some-> year to-year)

        :else
        (reify Expression
          (-eval [_ context scope]
            (some-> (-eval year context scope) to-year))
          (-hash [_]
            {:type :date
             :year (-hash year)}))))))


;; 18.11. DurationBetween
;;
;; The DurationBetween operator returns the number of whole calendar periods for
;; the specified precision between the first and second arguments. If the first
;; argument is after the second argument, the result is negative. The result of
;; this operation is always an integer; any fractional periods are dropped.
;;
;; For Date values, precision must be one of Year, Month, Week, or Day.
;;
;; For Time values, precision must be one of Hour, Minute, Second, or
;; Millisecond.
;;
;; For calculations involving weeks, the duration of a week is equivalent to 7
;; days.
;;
;; If either argument is null, the result is null.
;;
;; Note that this operator can be implemented using Uncertainty as described in
;; the CQL specification, Chapter 5, Precision-Based Timing.
(defn to-chrono-unit [precision]
  (case precision
    "Year" ChronoUnit/YEARS
    "Month" ChronoUnit/MONTHS
    "Week" ChronoUnit/WEEKS
    "Day" ChronoUnit/DAYS
    "Hour" ChronoUnit/HOURS
    "Minute" ChronoUnit/MINUTES
    "Second" ChronoUnit/SECONDS
    "Millisecond" ChronoUnit/MILLIS))

(defmethod compile* :elm.compiler.type/duration-between
  [context {operands :operand :keys [precision]}]
  (let [[operand-1 operand-2 :as operands] (mapv #(compile context %) operands)
        chrono-unit (to-chrono-unit precision)]
    (reify Expression
      (-eval [_ context scope]
        (p/duration-between
          (-eval operand-1 context scope)
          (-eval operand-2 context scope)
          chrono-unit))
      (-hash [_]
        {:type :duration-between
         :operands (mapv -hash operands)}))))


;; 18.13. Now
;;
;; The Now operator returns the date and time of the start timestamp associated
;; with the evaluation request. Now is defined in this way for two reasons:
;;
;; 1) The operation will always return the same value within any given
;; evaluation, ensuring that the result of an expression containing Now will
;; always return the same result.
;;
;; 2) The operation will return the timestamp associated with the evaluation
;; request, allowing the evaluation to be performed with the same timezone
;; offset information as the data delivered with the evaluation request.
(defmethod compile* :elm.compiler.type/now
  [_ _]
  (reify Expression
    (-eval [_ {:keys [now]} _]
      now)
    (-hash [_]
      {:type :now})))


;; 18.18. Time
(defmethod compile* :elm.compiler.type/time
  [context {:keys [hour minute second millisecond]}]
  (let [hour (some->> hour (compile context))
        minute (some->> minute (compile context))
        second (some->> second (compile context))
        millisecond (some->> millisecond (compile context))]
    (cond
      (and (int? millisecond) (int? second) (int? minute) (int? hour))
      (LocalTime/of hour minute second (* 1000 1000 millisecond))

      (some? millisecond)
      (reify Expression
        (-eval [_ context scope]
          (LocalTime/of (-eval hour context scope)
                        (-eval minute context scope)
                        (-eval second context scope)
                        (* 1000 1000 (-eval millisecond context scope))))
        (-hash [_]
          {:type :time
           :hour (-hash hour)
           :minute (-hash minute)
           :second (-hash second)
           :millisecond (-hash millisecond)}))

      (and (int? second) (int? minute) (int? hour))
      (LocalTime/of hour minute second 0)

      (some? second)
      (reify Expression
        (-eval [_ context scope]
          (LocalTime/of (-eval hour context scope)
                        (-eval minute context scope)
                        (-eval second context scope)))
        (-hash [_]
          {:type :time
           :hour (-hash hour)
           :minute (-hash minute)
           :second (-hash second)}))

      (and (int? minute) (int? hour))
      (LocalTime/of hour minute 0 0)

      (some? minute)
      (reify Expression
        (-eval [_ context scope]
          (LocalTime/of (-eval hour context scope)
                        (-eval minute context scope)))
        (-hash [_]
          {:type :time
           :hour (-hash hour)
           :minute (-hash minute)}))

      (int? hour)
      (LocalTime/of hour 0 0 0)

      :else
      (reify Expression
        (-eval [_ context scope]
          (LocalTime/of (-eval hour context scope) 0))
        (-hash [_]
          {:type :time
           :hour (-hash hour)})))))


;; 18.22. Today
;;
;; The Today operator returns the date (with no time component) of the start
;; timestamp associated with the evaluation request. See the Now operator for
;; more information on the rationale for defining the Today operator in this
;; way.
(defmethod compile* :elm.compiler.type/today
  [_ _]
  (reify Expression
    (-eval [_ {:keys [now]} _]
      (.toLocalDate ^OffsetDateTime now))
    (-hash [_]
      {:type :today})))



;; 19. Interval Operators

;; 19.1. Interval
(defmethod compile* :elm.compiler.type/interval
  [context {:keys [low high]
            low-closed-expression :lowClosedExpression
            high-closed-expression :highClosedExpression
            low-closed :lowClosed
            high-closed :lowClosed}]
  (let [low (some->> low (compile context))
        high (some->> high (compile context))
        low-closed-expression (some->> low-closed-expression (compile context))
        high-closed-expression (some->> high-closed-expression (compile context))]
    (cond
      (and (nil? low-closed-expression) (nil? high-closed-expression))
      (cond
        (and (literal? low) (literal? high))
        (interval low high (or low-closed true) (or high-closed true))))))


;; 19.15. Intersect
;;
;; The Intersect operator returns the intersection of its arguments.
;;
;; This operator has two overloads: List Interval
;;
;; For the list overload, this operator returns a list with the elements that
;; appear in both lists, using equality semantics. The operator is defined with
;; set semantics, meaning that each element will appear in the result at most
;; once, and that there is no expectation that the order of the inputs will be
;; preserved in the results.
;;
;; For the interval overload, this operator returns the interval that defines
;; the overlapping portion of both arguments. If the arguments do not overlap,
;; this operator returns null.
;;
;; If either argument is null, the result is null.
;;
;; TODO: Interval Implementation
(defmethod compile* :elm.compiler.type/intersect
  [context {operands :operand}]
  (let [operands (mapv #(compile context %) operands)]
    (reify Expression
      (-eval [_ context scope]
        (let [operands (remove nil? (map #(-eval % context scope) operands))]
          (when-not (empty? operands)
            (apply set/intersection (map set operands)))))
      (-hash [_]
        {:type :intersect
         :operands (mapv -hash operands)}))))


;; 19.30. Union
;;
;; The Union operator returns the union of its arguments.
;;
;; This operator has two overloads: List Interval
;;
;; For the list overload, this operator returns a list with all unique elements
;; from both arguments.
;;
;; For the interval overload, this operator returns the interval that starts at
;; the earliest starting point in either argument, and ends at the latest
;; starting point in either argument. If the arguments do not overlap or meet,
;; this operator returns null.
;;
;; If either argument is null, the result is null.
;;
;; TODO: Interval Implementation
(defmethod compile* :elm.compiler.type/union
  [context {operands :operand}]
  (let [operands (mapv #(compile context %) operands)]
    (reify Expression
      (-eval [_ context scope]
        (let [operands (remove nil? (map #(-eval % context scope) operands))]
          (when-not (empty? operands)
            (apply set/union (map set operands)))))
      (-hash [_]
        {:type :union
         :operands (mapv -hash operands)}))))



;; 20. List Operators

;; 20.1. List
;;
;; The List selector returns a value of type List, whose elements are the result
;; of evaluating the arguments to the List selector, in order.
;;
;; If a typeSpecifier element is provided, the list is of that type. Otherwise,
;; the static type of the first argument determines the type of the resulting
;; list, and each subsequent argument must be of that same type.
;;
;; If any argument is null, the resulting list will have null for that element.
(defmethod compile* :elm.compiler.type/list
  [context {elements :element}]
  (let [elements (mapv #(compile context %) elements)]
    (reify Expression
      (-eval [_ context scope]
        (mapv #(-eval % context scope) elements))
      (-hash [_]
        {:type :list
         :elements (mapv -hash elements)}))))


;; 20.25. SingletonFrom
;;
;; The SingletonFrom expression extracts a single element from the source list.
;; If the source list is empty, the result is null. If the source list contains
;; one element, that element is returned. If the list contains more than one
;; element, a run-time error is thrown. If the source list is null, the result
;; is null.
(defmethod compile* :elm.compiler.type/singleton-from
  [context {{:keys [locator] :as operand} :operand}]
  (let [operand (compile context operand)]
    (reify Expression
      (-eval [_ context scope]
        (let [operand (-eval operand context scope)]
          (cond
            (empty? operand) nil
            (nil? (next operand)) (first operand)
            :else (throw (Exception. (str "More than one element in expression `SingletonFrom` at " locator "."))))))
      (-hash [_]
        {:type :singleton-from
         :opernad (-hash operand)}))))



;; 21. Aggregate Operators

;; 21.4. Count
;;
;; The Count operator returns the number of non-null elements in the source.
;;
;; If a path is specified, the count returns the number of elements that have a
;; value for the property specified by the path.
;;
;; If the list is empty, the result is 0.
;;
;; If the list is null, the result is 0.
(defmethod compile* :elm.compiler.type/count
  [context {:keys [source]}]
  ;; TODO: path
  (let [source (compile context source)]
    (reify Expression
      (-eval [_ context scope]
        (transduce (comp (remove nil?) (map (constantly 1))) +
                   (-eval source context scope)))
      (-hash [_]
        {:type :count
         :source (-hash source)}))))



;; 22. Type Operators

;; 22.1. As
;;
;; The As operator allows the result of an expression to be cast as a given
;; target type. This allows expressions to be written that are statically typed
;; against the expected run-time type of the argument. If the argument is not of
;; the specified type, and the strict attribute is false (the default), the
;; result is null. If the argument is not of the specified type and the strict
;; attribute is true, an exception is thrown.
(defmethod compile* :elm.compiler.type/as
  [context {:keys [operand] as-type :asType as-type-specifier :asTypeSpecifier}]
  (let [as-type (or as-type (:name as-type-specifier))]
    (cond
      as-type
      (compile context (assoc operand :life/as-type-name as-type))
      :else
      (throw (Exception. "Unsupported `As` expression.")))))


;; 22.19. ToDate
;;
;; The ToDate operator converts the value of its argument to a Date value.
;;
;; For String values, The operator expects the string to be formatted using the
;; ISO-8601 date representation:
;;
;; YYYY-MM-DD
;;
;; In addition, the string must be interpretable as a valid date value.
;;
;; If the input string is not formatted correctly, or does not represent a valid
;; date value, the result is null.
;;
;; As with date literals, date values may be specified to any precision.
;;
;; For DateTime values, the result is equivalent to extracting the Date
;; component of the DateTime value.
;;
;; If the argument is null, the result is null.
(defmethod compile* :elm.compiler.type/to-date
  [context {:keys [operand]}]
  (let [operand (compile context operand)]
    (reify Expression
      (-eval [_ {:keys [now] :as context} scope]
        (p/to-date (-eval operand context scope) now))
      (-hash [_]
        {:type :to-date
         :operand (-hash operand)}))))


;; 22.20. ToDateTime
(defmethod compile* :elm.compiler.type/to-date-time
  [context {:keys [operand]}]
  (let [operand (compile context operand)]
    (reify Expression
      (-eval [_ {:keys [now] :as context} scope]
        (p/to-date-time (-eval operand context scope) now))
      (-hash [_]
        {:type :to-date-time
         :operand (-hash operand)}))))


;; 22.21. ToDecimal
(defunary-operator "to-decimal")


;; 22.23. ToList
;;
;; The ToList operator returns its argument as a List value. The operator
;; accepts a singleton value of any type and returns a list with the value as
;; the single element.
;;
;; If the argument is null, the operator returns an empty list.
;;
;; The operator is effectively shorthand for "if operand is null then { } else
;; { operand }".
;;
;; The operator is used to implement list promotion efficiently.
(defmethod compile* :elm.compiler.type/to-list
  [context {:keys [operand]}]
  (let [operand (compile context operand)]
    (reify Expression
      (-eval [_ context scope]
        (let [value (-eval operand context scope)]
          (if (nil? value) [] [value])))
      (-hash [_]
        {:type :to-list
         :operand (-hash operand)}))))


;; 22.24. ToInteger
(defunary-operator "to-integer")


;; 22.24. ToQuantity
;;
;; The ToQuantity operator converts the value of its argument to a Quantity
;; value. The operator accepts strings using the following format:
;;
;; (+|-)?0(.0)?('<unit>')?
(defmethod compile* :elm.compiler.type/to-quantity
  [context {:keys [operand]}]
  (when-let [result (compile context operand)]
    (if (number? result)
      result
      (throw (Exception. (str "Unsupported quantity `" result "`."))))))



;; 23. Clinical Operators

;; 23.4. CalculateAgeAt
;;
;; Calculates the age in the specified precision of a person born on the first
;; Date or DateTime as of the second Date or DateTime.
;;
;; The CalculateAgeAt operator has two signatures: Date, Date DateTime, DateTime
;;
;; For the Date overload, precision must be one of year, month, week, or day.
;;
;; The result of the calculation is the number of whole calendar periods that
;; have elapsed between the first date/time and the second.
(defmethod compile* :elm.compiler.type/calculate-age-at
  [context {operands :operand :keys [precision]}]
  (let [[operand-1 operand-2 :as operands] (mapv #(compile context %) operands)
        chrono-unit (to-chrono-unit precision)]
    (reify Expression
      (-eval [_ context scope]
        (p/duration-between
          (-eval operand-1 context scope)
          (-eval operand-2 context scope)
          chrono-unit))
      (-hash [_]
        {:type :calculate-age-at
         :operands (mapv -hash operands)}))))
