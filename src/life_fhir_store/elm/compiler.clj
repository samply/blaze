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
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [life-fhir-store.datomic.cql :as cql]
    [life-fhir-store.datomic.time :as time]
    [life-fhir-store.datomic.quantity :as quantity]
    [life-fhir-store.elm.deps-infer :refer [infer-library-deps]]
    [life-fhir-store.elm.equiv-relationships :refer [find-equiv-rels-library]]
    [life-fhir-store.elm.normalizer :refer [normalize-library]]
    [life-fhir-store.elm.spec]
    [life-fhir-store.elm.type-infer :refer [infer-library-types]]
    [life-fhir-store.elm.util :as elm-util])
  (:import
    [java.time LocalDate LocalDateTime OffsetDateTime Year YearMonth ZoneOffset]
    [java.time.temporal ChronoField ChronoUnit TemporalAccessor])
  (:refer-clojure :exclude [compile]))


(defprotocol Expression
  (-eval [this context scope])
  (-hash [this]))


(defprotocol Equal
  (equal [this other]))


(defprotocol Greater
  (greater [this other]))


(defprotocol GreaterOrEqual
  (greater-or-equal [this other]))


(defprotocol Less
  (less [this other]))


(defprotocol LessOrEqual
  (less-or-equal [this other]))


(defprotocol NotEqual
  (not-equal [this other]))


(defprotocol Abs
  (abs [this]))


(extend-protocol Expression
  nil
  (-eval [this _ _]
    this)
  (-hash [this]
    this)

  Boolean
  (-eval [this _ _]
    this)
  (-hash [this]
    this)

  Double
  (-eval [this _ _]
    this)
  (-hash [this]
    this)

  Long
  (-eval [this _ _]
    this)
  (-hash [this]
    this)

  String
  (-eval [this _ _]
    this)
  (-hash [this]
    this)

  Year
  (-eval [this _ _]
    this)
  (-hash [this]
    this)

  YearMonth
  (-eval [this _ _]
    this)
  (-hash [this]
    this)

  LocalDateTime
  (-eval [this _ _]
    this)
  (-hash [this]
    this)

  OffsetDateTime
  (-eval [this _ _]
    this)
  (-hash [this]
    this))


(extend-protocol Equal
  nil
  (equal [_ _])

  Object
  (equal [a b]
    (some->> b (= a))))


(extend-protocol Greater
  nil
  (greater [_ _])

  Number
  (greater [a b]
    (some->> b (> a))))


(extend-protocol GreaterOrEqual
  nil
  (greater-or-equal [_ _])

  Number
  (greater-or-equal [a b]
    (some->> b (>= a))))


(extend-protocol Less
  nil
  (less [_ _])

  Number
  (less [a b]
    (some->> b (< a))))


(extend-protocol LessOrEqual
  nil
  (less-or-equal [_ _])

  Number
  (less-or-equal [a b]
    (some->> b (<= a))))


(extend-protocol Abs
  Double
  (abs [this]
    (Math/abs ^double this))

  Long
  (abs [this]
    (Math/abs ^long this)))



;; --- DateTime ---------------------------------------------------------------

(defprotocol DurationBetween
  "Returns the duration in `chrono-unit` between `this` and `other` if the
  precisions are sufficient."
  (duration-between [this other chrono-unit]))


(defprotocol Precision
  "Returns the precision of a date-time instance."
  (precision [this]))


(defprotocol ToDateTime
  "Converts an object into something usable as DateTime relative to `now`.

  Converts OffsetDateTime and Instant to LocalDateTime so that we can compare
  temporal fields directly."
  (to-date-time [this now]))


(defn- get-chrono-field [^TemporalAccessor ta ^long precision]
  (.getLong ta (case precision
                 0 ChronoField/YEAR
                 1 ChronoField/MONTH_OF_YEAR
                 2 ChronoField/DAY_OF_MONTH
                 3 ChronoField/HOUR_OF_DAY
                 4 ChronoField/MINUTE_OF_HOUR
                 5 ChronoField/SECOND_OF_MINUTE)))


(defn- compare-to-precision
  "Compares two date time values up to the minimum of the specified precisions.

  Returns nil (unknown) if the fields up to the smaller precision are equal but
  one of the precisions is higher."
  [dt-1 dt-2 p-1 p-2]
  (let [min-precision (min p-1 p-2)]
    (loop [precision 0]
      (let [cmp (- (get-chrono-field dt-1 precision)
                   (get-chrono-field dt-2 precision))]
        (if (zero? cmp)
          (if (< precision min-precision)
            (recur (inc precision))
            (when (= p-1 p-2) 0))
          cmp)))))


(def ^:private chrono-unit->precision
  {ChronoUnit/YEARS 0
   ChronoUnit/MONTHS 1
   ChronoUnit/WEEKS 2
   ChronoUnit/DAYS 2
   ChronoUnit/HOURS 3
   ChronoUnit/MINUTES 4
   ChronoUnit/SECONDS 5})


(extend-protocol Precision
  Year
  (precision [_] 0)
  YearMonth
  (precision [_] 1)
  LocalDate
  (precision [_] 2)
  LocalDateTime
  (precision [_] 5))


(extend-protocol DurationBetween
  nil
  (duration-between [_ _ _])

  Year
  (duration-between [this other chrono-unit]
    (when other
      (when (<= (chrono-unit->precision chrono-unit) (min 0 (precision other)))
        (.until this other chrono-unit))))

  YearMonth
  (duration-between [this other chrono-unit]
    (when other
      (when (<= (chrono-unit->precision chrono-unit) (min 1 (precision other)))
        (.until this other chrono-unit))))

  LocalDate
  (duration-between [this other chrono-unit]
    (when other
      (when (<= (chrono-unit->precision chrono-unit) (min 2 (precision other)))
        (.until this other chrono-unit))))

  LocalDateTime
  (duration-between [this other chrono-unit]
    (when other
      (when (<= (chrono-unit->precision chrono-unit) (min 5 (precision other)))
        (.until this other chrono-unit)))))


(extend-protocol Greater
  Year
  (greater [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 0 (precision other))]
        (> cmp 0))))

  YearMonth
  (greater [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision other))]
        (> cmp 0))))

  LocalDate
  (greater [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision other))]
        (> cmp 0))))

  LocalDateTime
  (greater [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 5 (precision other))]
        (> cmp 0)))))


(extend-protocol GreaterOrEqual
  Year
  (greater-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 0 (precision other))]
        (>= cmp 0))))

  YearMonth
  (greater-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision other))]
        (>= cmp 0))))

  LocalDate
  (greater-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision other))]
        (>= cmp 0))))

  LocalDateTime
  (greater-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 5 (precision other))]
        (>= cmp 0)))))


(extend-protocol Less
  Year
  (less [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 0 (precision other))]
        (< cmp 0))))

  YearMonth
  (less [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision other))]
        (< cmp 0))))

  LocalDate
  (less [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision other))]
        (< cmp 0))))

  LocalDateTime
  (less [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 5 (precision other))]
        (< cmp 0)))))


(extend-protocol LessOrEqual
  Year
  (less-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 0 (precision other))]
        (<= cmp 0))))

  YearMonth
  (less-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision other))]
        (<= cmp 0))))

  LocalDate
  (less-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision other))]
        (<= cmp 0))))

  LocalDateTime
  (less-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 5 (precision other))]
        (<= cmp 0)))))


(extend-protocol ToDateTime
  nil
  (to-date-time [_ _])

  Year
  (to-date-time [this _]
    this)

  YearMonth
  (to-date-time [this _]
    this)

  LocalDate
  (to-date-time [this _]
    this)

  LocalDateTime
  (to-date-time [this _]
    this)

  OffsetDateTime
  (to-date-time [this now]
    (-> (.withOffsetSameInstant this (.getOffset ^OffsetDateTime now))
        (.toLocalDateTime))))



;; ---- Compiler --------------------------------------------------------------

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
          "Integer" (Long/parseLong value)
          "Decimal" (Double/parseDouble value)
          "String" value
          (throw (Exception. (str value-type-name " literals are not supported"))))))))


;; 2. Structured Values

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
(defmethod compile* :elm.compiler.type/property
  [{:life/keys [single-query-scope] :as context}
   {:keys [source scope path data-type] :life/keys [source-type]}]
  (assert source-type (str "Missing :life/source-type annotation on Property expression with source `" source "`, scope `" scope "` and path `" path "`."))
  (let [[source-type-ns source-type-name] (elm-util/parse-qualified-name source-type)
        path (str path data-type)
        attr-kw (keyword source-type-name path)
        source (some->> source (compile context))]
    (assert (= "http://hl7.org/fhir" source-type-ns))
    (cond
      ;; We evaluate the `source` to retrieve the entity.
      source
      (reify Expression
        (-eval [_ context scope]
          (attr-kw (-eval source context scope)))
        (-hash [_]
          {:type :property
           :attr-kw attr-kw
           :source (-hash source)}))

      ;; We use the `scope` to retrieve the entity from `query-context`.
      scope
      (if (= single-query-scope scope)
        (reify Expression
          (-eval [_ _ entity]
            (attr-kw entity))
          (-hash [_]
            {:type :property
             :attr-kw attr-kw}))
        (reify Expression
          (-eval [_ _ query-context]
            (attr-kw (get query-context scope)))
          (-hash [_]
            {:type :property
             :attr-kw attr-kw
             :scope scope}))))))



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
  (when unit
    (throw (Exception. "Units are not supported yet.")))
  value)



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

      "ToDateTime"
      (let [operand (first operands)]
        (reify Expression
          (-eval [_ {:keys [now] :as context} scope]
            (to-date-time (-eval operand context scope) now))
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
          xform (xform with-xforms where-xform return-xform)]
      (let [source (compile context expression)]
        (reify Expression
          (-eval [_ context _]
            (into #{} (xform context) (-eval source context nil)))
          (-hash [_]
            (cond->
              {:type :query
               :source (-hash source)}
              (some? where) (assoc :where (-hash where))
              (seq with-equiv-clauses) (assoc :with (-hash with-equiv-clauses))
              (some? return) (assoc :return (-hash return)))))))
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
(defmethod compile* :elm.compiler.type/equal
  [context {[operand-1 operand-2] :operand}]
  (let [operand-1 (compile context operand-1)
        operand-2 (compile context operand-2)]
    (reify Expression
      (-eval [_ context scope]
        (equal (-eval operand-1 context scope) (-eval operand-2 context scope)))
      (-hash [_]
        {:type :equal
         :operands [(-hash operand-1) (-hash operand-2)]}))))


;; TODO 12.2. Equivalent


;; 12.3. Greater
(defmethod compile* :elm.compiler.type/greater
  [context {[operand-1 operand-2] :operand}]
  (let [operand-1 (compile context operand-1)
        operand-2 (compile context operand-2)]
    (reify Expression
      (-eval [_ context scope]
        (greater (-eval operand-1 context scope) (-eval operand-2 context scope)))
      (-hash [_]
        {:type :greater
         :operands [(-hash operand-1) (-hash operand-2)]}))))


;; 12.4. GreaterOrEqual
(defmethod compile* :elm.compiler.type/greater-or-equal
  [context {[operand-1 operand-2] :operand}]
  (let [operand-1 (compile context operand-1)
        operand-2 (compile context operand-2)]
    (reify Expression
      (-eval [_ context scope]
        (greater-or-equal (-eval operand-1 context scope) (-eval operand-2 context scope)))
      (-hash [_]
        {:type :greater-or-equal
         :operands [(-hash operand-1) (-hash operand-2)]}))))


;; 12.5. Less
(defmethod compile* :elm.compiler.type/less
  [context {[operand-1 operand-2] :operand}]
  (let [operand-1 (compile context operand-1)
        operand-2 (compile context operand-2)]
    (reify Expression
      (-eval [_ context scope]
        (less (-eval operand-1 context scope) (-eval operand-2 context scope)))
      (-hash [_]
        {:type :less
         :operands [(-hash operand-1) (-hash operand-2)]}))))


;; 12.6. LessOrEqual
(defmethod compile* :elm.compiler.type/less-or-equal
  [context {[operand-1 operand-2] :operand}]
  (let [operand-1 (compile context operand-1)
        operand-2 (compile context operand-2)]
    (reify Expression
      (-eval [_ context scope]
        (less-or-equal (-eval operand-1 context scope) (-eval operand-2 context scope)))
      (-hash [_]
        {:type :less-or-equal
         :operands [(-hash operand-1) (-hash operand-2)]}))))


;; 12.7. NotEqual
(defmethod compile* :elm.compiler.type/not-equal
  [context {[operand-1 operand-2] :operand}]
  (let [operand-1 (compile context operand-1)
        operand-2 (compile context operand-2)]
    (reify Expression
      (-eval [_ context scope]
        (not-equal (-eval operand-1 context scope) (-eval operand-2 context scope)))
      (-hash [_]
        {:type :not-equal
         :operands [(-hash operand-1) (-hash operand-2)]}))))



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
                (and (true? operand-1) (true? operand-2)) true))))))))


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
            (false? operand) true))))))


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
                (and (false? operand-1) (false? operand-2)) false))))))))


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
          operands)))))


;; 14.3. IsFalse
(defmethod compile* :elm.compiler.type/is-false
  [context {:keys [operand]}]
  (let [operand (compile context operand)]
    (reify Expression
      (-eval [_ context scope]
        (false? (-eval operand context scope))))))


;; 14.4. IsNull
(defmethod compile* :elm.compiler.type/is-null
  [context {:keys [operand]}]
  (let [operand (compile context operand)]
    (reify Expression
      (-eval [_ context scope]
        (nil? (-eval operand context scope))))))


;; 14.5. IsTrue
;;
;; The IsTrue operator determines whether or not its argument evaluates to true.
;; If the argument evaluates to true, the result is true; if the argument
;; evaluates to false or null, the result is false.
(defmethod compile* :elm.compiler.type/is-true
  [context {:keys [operand]}]
  (let [operand (compile context operand)]
    (reify Expression
      (-eval [_ context scope]
        (true? (-eval operand context scope))))))



;; 16. Arithmetic Operators

;; 16.1. Abs
(defmethod compile* :elm.compiler.type/abs
  [context {:keys [operand]}]
  (let [operand (compile context operand)]
    (reify Expression
      (-eval [_ context scope]
        (abs (-eval operand context scope))))))



;; 18. Date and Time Operators

(defn- to-year [year]
  (Year/of ^long year))


(defn- to-month [year month]
  (YearMonth/of ^long year ^long month))


(defn- to-day [year month day]
  (LocalDate/of ^long year ^long month ^long day))


(defn- to-hour [year month day hour]
  (LocalDateTime/of ^long year ^long month ^long day ^long hour 0 0 0))


(defn- to-minute [year month day hour minute]
  (LocalDateTime/of ^long year ^long month ^long day
                    ^long hour ^long minute 0 0))


(defn- to-second [year month day hour minute second]
  (LocalDateTime/of ^long year ^long month ^long day
                    ^long hour ^long minute ^long second 0))


(defn- to-timezone-offset
  "Creates a DateTime with a local date time adjusted for the offset of the
  evaluation request."
  [now year month day hour minute second timezone-offset]
  (-> (OffsetDateTime/of ^long year ^long month ^long day ^long hour
                         ^long minute ^long second 0
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
      day
      (reify Expression
        (-eval [_ _ _]
          (to-day year month day))
        (-hash [_]
          {:type :date
           :year year
           :month month
           :day day}))

      month
      (reify Expression
        (-eval [_ _ _]
          (to-month year month))
        (-hash [_]
          {:type :date
           :year year
           :month month}))

      year
      (reify Expression
        (-eval [_ _ _]
          (to-year year))
        (-hash [_]
          {:type :date
           :year year})))))


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
  [context {:keys [year month day hour minute second timezone-offset]}]
  (let [year (some->> year (compile context))
        month (some->> month (compile context))
        day (some->> day (compile context))
        hour (some->> hour (compile context))
        minute (some->> minute (compile context))
        second (some->> second (compile context))
        timezone-offset (some->> timezone-offset (compile context))]
    (cond
      timezone-offset
      (reify Expression
        (-eval [_ {:keys [now]} _]
          (to-timezone-offset now year month day hour minute second timezone-offset))
        (-hash [_]
          {:type :date-time
           :year year
           :month month
           :day day
           :hour hour
           :minute minute
           :second second
           :timezone-offset timezone-offset}))

      second
      (reify Expression
        (-eval [_ _ _]
          (to-second year month day hour minute second))
        (-hash [_]
          {:type :date-time
           :year year
           :month month
           :day day
           :hour hour
           :minute minute
           :second second}))

      minute
      (reify Expression
        (-eval [_ _ _]
          (to-minute year month day hour minute))
        (-hash [_]
          {:type :date-time
           :year year
           :month month
           :day day
           :hour hour
           :minute minute}))

      hour
      (reify Expression
        (-eval [_ _ _]
          (to-hour year month day hour))
        (-hash [_]
          {:type :date-time
           :year year
           :month month
           :day day
           :hour hour}))

      day
      (reify Expression
        (-eval [_ _ _]
          (to-day year month day))
        (-hash [_]
          {:type :date-time
           :year year
           :month month
           :day day}))

      month
      (reify Expression
        (-eval [_ _ _]
          (to-month year month))
        (-hash [_]
          {:type :date-time
           :year year
           :month month}))

      year
      (reify Expression
        (-eval [_ _ _]
          (to-year year))
        (-hash [_]
          {:type :date-time
           :year year})))))


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
  (let [[operand-1 operand-2] (mapv #(compile context %) operands)
        chrono-unit (to-chrono-unit precision)]
    (reify Expression
      (-eval [_ context scope]
        (duration-between
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
  [context {:keys [operand]}]
  (let [operand (compile context operand)]
    (reify Expression
      (-eval [_ context scope]
        (let [operand (-eval operand context scope)]
          (cond
            (empty? operand) nil
            (nil? (next operand)) (first operand)
            :else (throw (Exception. "more than one element in SingletonFrom")))))
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
;;
;; TODO: This operator is used to actively convert types. That's wrong!
(defn title-case [s]
  (let [[first & rest] s]
    (apply str (str/upper-case (str first)) rest)))

(defmethod compile* :elm.compiler.type/as
  [context {:keys [operand] as-type :asType as-type-specifier :asTypeSpecifier}]
  (let [as-type (or as-type (:name as-type-specifier))]
    (cond
      as-type
      (when-let [[type-ns type-name] (elm-util/parse-qualified-name as-type)]
        (case type-ns
          "http://hl7.org/fhir"
          (let [operand (compile context (assoc operand :data-type (title-case type-name)))]
            (case type-name
              "Quantity"
              (reify Expression
                (-eval [_ context scope]
                  (some-> (-eval operand context scope) quantity/read)))
              ("date" "time" "dateTime")
              (reify Expression
                (-eval [_ context scope]
                  (some-> (-eval operand context scope) time/read)))
              operand))
          (throw (Exception. (str "Unsupported type namespace `" type-ns "` in `As` expression.")))))
      :else
      (throw (Exception. "Unsupported `As` expression.")))))


;; 22.20. ToDateTime
(defmethod compile* :elm.compiler.type/to-date-time
  [context {:keys [operand]}]
  (compile context operand))


;; 22.23. ToList
;;
;; The ToList operator returns its argument as a List value. The operator
;; accepts a singleton value of any type and returns a list with the value as
;; the single element.
(defmethod compile* :elm.compiler.type/to-list
  [context {:keys [operand]}]
  (let [operand (compile context operand)]
    (reify Expression
      (-eval [_ context scope]
        (some-> (-eval operand context scope) vector))
      (-hash [_]
        {:type :to-list
         :operand (-hash operand)}))))


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
