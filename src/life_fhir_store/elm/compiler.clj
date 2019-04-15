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
    [life-fhir-store.elm.date-time :as date-time :refer [local-time]]
    [life-fhir-store.elm.decimal :as decimal]
    [life-fhir-store.elm.deps-infer :refer [infer-library-deps]]
    [life-fhir-store.elm.equiv-relationships :refer [find-equiv-rels-library]]
    [life-fhir-store.elm.integer]
    [life-fhir-store.elm.interval :refer [interval interval?]]
    [life-fhir-store.elm.nil]
    [life-fhir-store.elm.normalizer :refer [normalize-library]]
    [life-fhir-store.elm.protocols :as p]
    [life-fhir-store.elm.quantity :refer [quantity]]
    [life-fhir-store.elm.spec]
    [life-fhir-store.elm.type-infer :refer [infer-library-types]]
    [life-fhir-store.elm.util :as elm-util]
    [life-fhir-store.util :as u])
  (:import
    [java.time LocalDate LocalDateTime LocalTime OffsetDateTime Year YearMonth
               ZoneOffset]
    [java.time.temporal ChronoUnit Temporal]
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


(defmacro defunop
  {:arglists '([name bindings & body])}
  [name [operand-binding expr-binding] & body]
  `(defmethod compile* ~(keyword "elm.compiler.type" (clojure.core/name name))
     [context# expr#]
     (let [operand# (compile context# (:operand expr#))
           ~(or expr-binding '_) expr#]
       (reify Expression
         (-eval [~'_ context# scope#]
           (let [~operand-binding (-eval operand# context# scope#)]
             ~@body))
         (-hash [_]
           {:type ~(keyword (clojure.core/name name))
            :operands (-hash operand#)})))))



(defmacro defbinop
  {:arglists '([name bindings & body])}
  [name [op-1-binding op-2-binding] & body]
  `(defmethod compile* ~(keyword "elm.compiler.type" (clojure.core/name name))
     [context# {[operand-1# operand-2#] :operand}]
     (let [operand-1# (compile context# operand-1#)
           operand-2# (compile context# operand-2#)]
       (reify Expression
         (-eval [~'_ context# scope#]
           (let [~op-1-binding (-eval operand-1# context# scope#)
                 ~op-2-binding (-eval operand-2# context# scope#)]
             ~@body))
         (-hash [_]
           {:type ~(keyword (clojure.core/name name))
            :operands [(-hash operand-1#) (-hash operand-2#)]})))))


(defmacro defnaryop
  {:arglists '([name bindings & body])}
  [name [operands-binding] & body]
  `(defmethod compile* ~(keyword "elm.compiler.type" (clojure.core/name name))
     [context# {operands# :operand}]
     (let [operands# (mapv #(compile context# %) operands#)]
       (reify Expression
         (-eval [~'_ context# scope#]
           (let [~operands-binding (mapv #(-eval % context# scope#) operands#)]
             ~@body))
         (-hash [_]
           {:type ~(keyword (clojure.core/name name))
            :operands (mapv -hash operands#)})))))


(defn- to-chrono-unit [precision]
  (case precision
    "Year" ChronoUnit/YEARS
    "Month" ChronoUnit/MONTHS
    "Week" ChronoUnit/WEEKS
    "Day" ChronoUnit/DAYS
    "Hour" ChronoUnit/HOURS
    "Minute" ChronoUnit/MINUTES
    "Second" ChronoUnit/SECONDS
    "Millisecond" ChronoUnit/MILLIS))


(defmacro defbinopp
  {:arglists '([name bindings & body])}
  [name [sym-op-1 sym-op-2 sym-precision] & body]
  `(defmethod compile* ~(keyword "elm.compiler.type" (clojure.core/name name))
     [context# {[operand-1# operand-2#] :operand precision# :precision}]
     (let [operand-1# (compile context# operand-1#)
           operand-2# (compile context# operand-2#)
           ~sym-precision (some-> precision# to-chrono-unit)]
       (reify Expression
         (-eval [~'_ context# scope#]
           (let [~sym-op-1 (-eval operand-1# context# scope#)
                 ~sym-op-2 (-eval operand-2# context# scope#)]
             ~@body))
         (-hash [_]
           {:type ~(keyword (clojure.core/name name))
            :operands [(-hash operand-1#) (-hash operand-2#)]})))))


(defn- literal? [x]
  (or (boolean? x)
      (number? x)
      (string? x)
      (instance? Temporal x)
      (instance? Quantity x)))


(defn- append-locator [msg locator]
  (if locator
    (str msg " " locator ".")
    (str msg ".")))



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
  {:arglists '([choice-type-specifier type])}
  [{types :type} type]
  (some #(and (= (:type type) (:type %))
              (= (:name type) (:name %))) types))


(defn- extract-local-fhir-name [type-name]
  (let [[ns name] (elm-util/parse-qualified-name type-name)]
    (if (= "http://hl7.org/fhir" ns)
      name
      (throw (Exception. (str "Unsupported type namespace `"
                              ns "` in `Property` expression."))))))


(defn attr-kw
  {:arglists '([property-expression])}
  [{:life/keys [source-type as-type] :keys [path source]
    result-type-specifier :resultTypeSpecifier :as expr}]
  (cond
    source-type
    (let [[source-type-ns source-type-name] (elm-util/parse-qualified-name source-type)]
      (if (= "http://hl7.org/fhir" source-type-ns)
        (if (choice-type-specifier? result-type-specifier)
          (if (contains-choice-type? result-type-specifier as-type)
            (keyword source-type-name (str path (u/title-case (extract-local-fhir-name (:name as-type)))))
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
    :life/keys [as-type]
    :as expr}]
  (cond
    result-type-name
    (elm-util/parse-qualified-name result-type-name)

    (choice-type-specifier? result-type-specifier)
    (if (contains-choice-type? result-type-specifier as-type)
      (elm-util/parse-qualified-name (:name as-type))
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
      (quantity value unit))
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
(defbinop equal [operand-1 operand-2]
  (p/equal operand-1 operand-2))


;; 12.2. Equivalent
(defbinop equivalent [operand-1 operand-2]
  (p/equivalent operand-1 operand-2))


;; 12.3. Greater
(defbinop greater [operand-1 operand-2]
  (p/greater operand-1 operand-2))


;; 12.4. GreaterOrEqual
(defbinop greater-or-equal [operand-1 operand-2]
  (p/greater-or-equal operand-1 operand-2))


;; 12.5. Less
(defbinop less [operand-1 operand-2]
  (p/less operand-1 operand-2))


;; 12.6. LessOrEqual
(defbinop less-or-equal [operand-1 operand-2]
  (p/less-or-equal operand-1 operand-2))


;; 12.7. NotEqual
(defmethod compile* :elm.compiler.type/not-equal
  [_ _]
  (throw (Exception. "Unsupported NotEqual expression. Please normalize the ELM tree before compiling.")))


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
(defunop not [operand]
  (cond
    (true? operand) false
    (false? operand) true))


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
  (condp = (count operands)
    1
    (let [operand (first operands)]
      (if (= "List" (:type operand))
        (let [operand (compile context operand)]
          (reify Expression
            (-eval [_ context scope]
              (reduce
                (fn [_ elem]
                  (let [elem (-eval elem context scope)]
                    (when (some? elem)
                      (reduced elem))))
                nil
                (-eval operand context scope)))
            (-hash [_]
              {:type :coalesce
               :operands (mapv -hash operands)})))
        (let [operand (compile context operand)]
          (reify Expression
            (-eval [_ context scope]
              (-eval operand context scope))
            (-hash [_]
              {:type :coalesce
               :operands (mapv -hash operands)})))))
    (let [operands (mapv #(compile context %) operands)]
      (reify Expression
        (-eval [_ context scope]
          (reduce
            (fn [_ operand]
              (let [operand (-eval operand context scope)]
                (when (some? operand)
                  (reduced operand))))
            nil
            operands))
        (-hash [_]
          {:type :coalesce
           :operands (mapv -hash operands)})))))


;; 14.3. IsFalse
(defunop is-false [operand]
  (false? operand))


;; 14.4. IsNull
(defunop is-null [operand]
  (nil? operand))


;; 14.5. IsTrue
(defunop is-true [operand]
  (true? operand))


;; 15. Conditional Operators

;; 15.1. Case
(defmethod compile* :elm.compiler.type/case
  [context {:keys [comparand else] items :caseItem}]
  (let [comparand (some->> comparand (compile context))
        items (mapv #(-> % (update :when (partial compile context))
                         (update :then (partial compile context)))
                    items)
        else (compile context else)]
    (if comparand
      (reify Expression
        (-eval [_ context scope]
          (let [comparand (-eval comparand context scope)]
            (loop [[{:keys [when then]} & next-items] items]
              (if (p/equal comparand (-eval when context scope))
                (-eval then context scope)
                (if (empty? next-items)
                  (-eval else context scope)
                  (recur next-items))))))
        (-hash [_]
          (cond->
            {:type :case
             :items (mapv #(-> % (update :when -hash) (update :then -hash)) items)
             :else (-hash else)}
            comparand
            (assoc :comparand (-hash comparand)))))
      (reify Expression
        (-eval [_ context scope]
          (loop [[{:keys [when then]} & next-items] items]
            (if (-eval when context scope)
              (-eval then context scope)
              (if (empty? next-items)
                (-eval else context scope)
                (recur next-items)))))
        (-hash [_]
          (cond->
            {:type :case
             :items (mapv #(-> % (update :when -hash) (update :then -hash)) items)
             :else (-hash else)}
            comparand
            (assoc :comparand (-hash comparand))))))))


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
(defunop abs [x]
  (p/abs x))


;; 16.2. Add
(defbinop add [x y]
  (p/add x y))


;; 16.3. Ceiling
(defunop ceiling [x]
  (p/ceiling x))


;; 16.4. Divide
(defbinop divide [x y]
  (p/divide x y))


;; 16.5. Exp
(defunop exp [x]
  (p/exp x))


;; 16.6. Floor
(defunop floor [x]
  (p/floor x))


;; 16.7. Log
(defbinop log [x base]
  (p/log x base))


;; 16.8. Ln
(defunop ln [x]
  (p/ln x))


;; 16.9. MaxValue
(defn max-value [type]
  (let [[ns name] (elm-util/parse-qualified-name type)]
    (case ns
      "urn:hl7-org:elm-types:r1"
      (case name
        "Integer" (long Integer/MAX_VALUE)
        "Decimal" decimal/max
        "Date" date-time/max-date
        "DateTime" date-time/max-date-time
        "Time" date-time/max-time
        (throw (ex-info (str "Unsupported type `" name "` for MaxValue.")
                        {:type type})))
      (throw (ex-info (str "Unknown type namespace `" ns "`.") {:type type})))))


(defmethod compile* :elm.compiler.type/max-value
  [_ {type :valueType}]
  (max-value type))


;; 16.10. MinValue
(defn min-value [type]
  (let [[ns name] (elm-util/parse-qualified-name type)]
    (case ns
      "urn:hl7-org:elm-types:r1"
      (case name
        "Integer" (long Integer/MIN_VALUE)
        "Decimal" decimal/min
        "Date" date-time/min-date
        "DateTime" date-time/min-date-time
        "Time" date-time/min-time
        (throw (ex-info (str "Unsupported type `" name "` for MinValue.")
                        {:type type})))
      (throw (ex-info (str "Unknown type namespace `" ns "`.") {:type type})))))


(defmethod compile* :elm.compiler.type/min-value
  [_ {type :valueType}]
  (min-value type))


;; 16.11. Modulo
(defbinop modulo [num div]
  (p/modulo num div))


;; 16.12. Multiply
(defbinop multiply [x y]
  (p/multiply x y))


;; 16.13. Negate
(defunop negate [x]
  (p/negate x))


;; 16.14. Power
(defbinop power [x exp]
  (p/power x exp))


;; 16.15. Predecessor
(defunop predecessor [x]
  (p/predecessor x))


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
(defbinop subtract [x y]
  (p/subtract x y))


;; 16.18. Successor
(defunop successor [x]
  (p/successor x))


;; 16.19. Truncate
(defunop truncate [x]
  (p/truncate x))


;; 16.20. TruncatedDivide
(defbinop truncated-divide [num div]
  (p/truncated-divide num div))



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
(defbinopp duration-between
  [operand-1 operand-2 precision]
  (p/duration-between operand-1 operand-2 precision))


;; 18.13. Now
(defmethod compile* :elm.compiler.type/now
  [_ _]
  (reify Expression
    (-eval [_ {:keys [now]} _]
      now)
    (-hash [_]
      {:type :now})))


;; 18.15. SameOrBefore
(defbinopp same-or-before [x y precision]
  (p/same-or-before x y precision))


;; 18.16. SameOrAfter
(defbinopp same-or-after [x y precision]
  (p/same-or-after x y precision))


;; 18.18. Time
(defmethod compile* :elm.compiler.type/time
  [context {:keys [hour minute second millisecond]}]
  (let [hour (some->> hour (compile context))
        minute (some->> minute (compile context))
        second (some->> second (compile context))
        millisecond (some->> millisecond (compile context))]
    (cond
      (and (int? millisecond) (int? second) (int? minute) (int? hour))
      (local-time hour minute second millisecond)

      (some? millisecond)
      (reify Expression
        (-eval [_ context scope]
          (local-time (-eval hour context scope)
                      (-eval minute context scope)
                      (-eval second context scope)
                      (-eval millisecond context scope)))
        (-hash [_]
          {:type :time
           :hour (-hash hour)
           :minute (-hash minute)
           :second (-hash second)
           :millisecond (-hash millisecond)}))

      (and (int? second) (int? minute) (int? hour))
      (local-time hour minute second)

      (some? second)
      (reify Expression
        (-eval [_ context scope]
          (local-time (-eval hour context scope)
                      (-eval minute context scope)
                      (-eval second context scope)))
        (-hash [_]
          {:type :time
           :hour (-hash hour)
           :minute (-hash minute)
           :second (-hash second)}))

      (and (int? minute) (int? hour))
      (local-time hour minute)

      (some? minute)
      (reify Expression
        (-eval [_ context scope]
          (local-time (-eval hour context scope)
                      (-eval minute context scope)))
        (-hash [_]
          {:type :time
           :hour (-hash hour)
           :minute (-hash minute)}))

      (int? hour)
      (local-time hour)

      :else
      (reify Expression
        (-eval [_ context scope]
          (local-time (-eval hour context scope)))
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
(defn- determine-type [{:keys [resultTypeName asType]}]
  (or resultTypeName asType))

(defmethod compile* :elm.compiler.type/interval
  [context {:keys [low high]
            low-closed-expression :lowClosedExpression
            high-closed-expression :highClosedExpression
            low-closed :lowClosed
            high-closed :highClosed
            :or {low-closed true high-closed true}}]
  (let [type (determine-type low)
        low (some->> low (compile context))
        high (some->> high (compile context))
        low-closed-expression (some->> low-closed-expression (compile context))
        high-closed-expression (some->> high-closed-expression (compile context))]
    (assert (string? type) (prn-str low))
    (reify Expression
      (-eval [_ context scope]
        (let [low (-eval low context scope)
              high (-eval high context scope)
              low-closed (or (-eval low-closed-expression context scope) low-closed)
              high-closed (or (-eval high-closed-expression context scope) high-closed)]
          (interval
            (if low-closed
              (if (nil? low)
                (min-value type)
                low)
              (p/successor low))
            (if high-closed
              (if (nil? high)
                (max-value type)
                high)
              (p/predecessor high)))))
      (-hash [_]
        (cond->
          {:type :interval}
          low
          (assoc :low (-hash low))
          high
          (assoc :high (-hash high))
          low-closed-expression
          (assoc :low-closed-expression (-hash low-closed-expression))
          high-closed-expression
          (assoc :high-closed-expression (-hash high-closed-expression))
          low-closed
          (assoc :low-closed low-closed)
          high-closed
          (assoc :high-closed high-closed))))))


;; 19.2. After
(defbinopp after [operand-1 operand-2 precision]
  (p/after operand-1 operand-2 precision))


;; 19.3. Before
(defbinopp before [operand-1 operand-2 precision]
  (p/before operand-1 operand-2 precision))


;; 19.4. Collapse
(defbinop collapse [source per]
  (when source
    (let [source (sort-by :start (remove nil? source))]
      (reverse
        (reduce
          (fn [r right]
            (let [[left & others] r]
              (if (p/greater-or-equal (:end left) (p/predecessor (:start right)))
                (cons (interval (:start left) (:end right)) others)
                (cons right r))))
          (cond-> (list) (first source) (conj (first source)))
          (rest source))))))


;; 19.5. Contains
(defbinopp contains [list-or-interval x precision]
  (p/contains list-or-interval x precision))


;; 19.6. End
(defunop end [{:keys [end]}]
  end)


;; 19.7. Ends
(defbinop ends [x y _]
  (and (p/greater-or-equal (:start x) (:start y))
       (p/equal (:end x) (:end y))))


;; 19.10. Except
(defbinop except [x y]
  (p/except x y))


;; 19.12. In
(defmethod compile* :elm.compiler.type/in
  [_ _]
  (throw (Exception. "Unsupported In expression. Please normalize the ELM tree before compiling.")))


;; 19.13. Includes
(defbinopp includes [x y precision]
  (p/includes x y precision))


;; 19.14. IncludedIn
(defmethod compile* :elm.compiler.type/included-in
  [_ _]
  (throw (Exception. "Unsupported IncludedIn expression. Please normalize the ELM tree before compiling.")))


;; 19.15. Intersect
(defbinop intersect [x y]
  (when (and (some? x) (some? y))
    (if (interval? x)
      (let [[left right] (if (p/less (:start x) (:start y)) [x y] [y x])]
        (when (p/greater-or-equal (:end left) (:start right))
          (some->> (if (p/less (:end left) (:end right))
                     (:end left)
                     (:end right))
                   (interval (:start right)))))
      (set/intersection (set x) (set y)))))


;; 19.16. Meets
(defmethod compile* :elm.compiler.type/meets
  [_ _]
  (throw (Exception. "Unsupported Meets expression. Please normalize the ELM tree before compiling.")))


;; 19.17. MeetsBefore
(defbinopp meets-before [x y _]
  (p/equal (:end x) (p/predecessor (:start y))))


;; 19.18. MeetsAfter
(defbinopp meets-after [x y _]
  (p/equal (:start x) (p/successor (:end y))))


;; 19.20. Overlaps
(defmethod compile* :elm.compiler.type/overlaps
  [_ _]
  (throw (Exception. "Unsupported Overlaps expression. Please normalize the ELM tree before compiling.")))


;; 19.21. OverlapsBefore
(defmethod compile* :elm.compiler.type/overlaps-before
  [_ _]
  (throw (Exception. "Unsupported OverlapsBefore expression. Please normalize the ELM tree before compiling.")))


;; 19.22. OverlapsAfter
(defmethod compile* :elm.compiler.type/overlaps-after
  [_ _]
  (throw (Exception. "Unsupported OverlapsAfter expression. Please normalize the ELM tree before compiling.")))


;; 19.23. PointFrom
(defunop point-from [interval {{:keys [locator]} :operand :as expression}]
  (when interval
    (if (p/equal (:start interval) (:end interval))
      (:start interval)
      (throw (ex-info (append-locator "Invalid non-unit interval in `PointFrom` expression at" locator)
                      {:expression expression})))))


;; 19.24. ProperContains
(defbinopp proper-contains [list-or-interval x precision]
  (p/proper-contains list-or-interval x precision))


;; 19.25. ProperIn
(defmethod compile* :elm.compiler.type/proper-in
  [_ _]
  (throw (Exception. "Unsupported ProperIn expression. Please normalize the ELM tree before compiling.")))


;; 19.26. ProperIncludes
(defbinopp proper-includes [x y precision]
  (p/proper-includes x y precision))


;; 19.27. ProperIncludedIn
(defmethod compile* :elm.compiler.type/proper-included-in
  [_ _]
  (throw (Exception. "Unsupported ProperIncludedIn expression. Please normalize the ELM tree before compiling.")))


;; 19.29. Start
(defunop start [{:keys [start]}]
  start)


;; 19.30. Starts
(defbinop starts [x y _]
  (and (p/equal (:start x) (:start y))
       (p/less-or-equal (:end x) (:end y))))


;; 19.31. Union
(defbinop union [x y]
  (when (and (some? x) (some? y))
    (if (interval? x)
      (let [[left right] (if (p/less (:start x) (:start y)) [x y] [y x])]
        (when (p/greater-or-equal (:end left) (p/predecessor (:start right)))
          (interval (:start left) (:end right))))
      (set/union (set x) (set y)))))


;; 19.32. Width
(defunop width [{:keys [start end]}]
  (p/subtract end start))



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
(defunop singleton-from [list {{:keys [locator]} :operand :as expression}]
  (cond
    (empty? list) nil
    (nil? (next list)) (first list)
    :else (throw (ex-info (append-locator "More than one element in expression `SingletonFrom` at" locator)
                          {:expression expression}))))



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
  (let [as-type-specifier
        (or as-type-specifier {:type "NamedTypeSpecifier" :name as-type})]
    (compile context (assoc operand :life/as-type as-type-specifier))))

;; 22.2. CanConvert

;; 22.3. CanConvertQuantity

;; 22.4. Children

;; 22.5. Convert

;; 22.6. ConvertQuantity

;; 22.7. ConvertsToBoolean

;; 22.8. ConvertsToDate

;; 22.9. ConvertsToDateTime

;; 22.10. ConvertsToDecimal

;; 22.11. ConvertsToInteger

;; 22.12. ConvertsToQuantity

;; 22.13. ConvertsToRatio

;; 22.14. ConvertsToString

;; 22.15. ConvertsToTime

;; 22.16. Descendents

;; 22.17. Is

;; 22.18. ToBoolean

;; 22.19. ToChars

;; 22.20. ToConcept

;; 22.21. ToDate
(defmethod compile* :elm.compiler.type/to-date
  [context {:keys [operand]}]
  (let [operand (compile context operand)]
    (reify Expression
      (-eval [_ {:keys [now] :as context} scope]
        (p/to-date (-eval operand context scope) now))
      (-hash [_]
        {:type :to-date
         :operand (-hash operand)}))))


;; 22.22. ToDateTime
(defmethod compile* :elm.compiler.type/to-date-time
  [context {:keys [operand]}]
  (let [operand (compile context operand)]
    (reify Expression
      (-eval [_ {:keys [now] :as context} scope]
        (p/to-date-time (-eval operand context scope) now))
      (-hash [_]
        {:type :to-date-time
         :operand (-hash operand)}))))


;; 22.23. ToDecimal
(defunop to-decimal [x]
  (p/to-decimal x))


;; 22.24. ToInteger
(defunop to-integer [x]
  (p/to-integer x))


;; 22.25. ToList
(defunop to-list [x]
  (if (nil? x) [] [x]))


;; 22.26. ToQuantity
(defunop to-quantity [x]
  (p/to-quantity x))



;; 23. Clinical Operators

;; 23.4. CalculateAgeAt
(defbinopp calculate-age-at [operand-1 operand-2 precision]
  (p/duration-between operand-1 operand-2 precision))
