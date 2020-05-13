(ns blaze.elm.compiler
  "Compiles ELM expressions to expressions defined by the `Expression`
  protocol in this namespace.

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html.

  Regarding time zones:
    We use date and time values with and without time zone information here.
    Every local (without time zone) date or time is meant relative to the time
    zone of the :now timestamp in the evaluation context."
  (:require
    [blaze.anomaly :refer [throw-anom when-ok]]
    [blaze.db.api :as d]
    [blaze.db.api-spec]
    [blaze.elm.compiler.function :as function]
    [blaze.elm.compiler.protocols :refer [Expression -eval]]
    [blaze.elm.compiler.retrieve :as retrieve]
    [blaze.elm.compiler.query :as query]
    [blaze.elm.aggregates :as aggregates]
    [blaze.elm.boolean]
    [blaze.elm.code :as code]
    [blaze.elm.data-provider :as data-provider]
    [blaze.elm.date-time :as date-time :refer [local-time temporal?]]
    [blaze.elm.decimal :as decimal]
    [blaze.elm.deps-infer :as deps-infer]
    [blaze.elm.equiv-relationships :as equiv-relationships]
    [blaze.elm.integer]
    [blaze.elm.interval :refer [interval]]
    [blaze.elm.list]
    [blaze.elm.nil]
    [blaze.elm.normalizer :as normalizer]
    [blaze.elm.protocols :as p]
    [blaze.elm.quantity :refer [quantity quantity?]]
    [blaze.elm.spec]
    [blaze.elm.string :as string]
    [blaze.elm.type-infer :as type-infer]
    [blaze.elm.util :as elm-util]
    [blaze.fhir.spec :as fhir-spec]
    [cognitect.anomalies :as anom]
    [cuerdas.core :as str])
  (:import
    [java.time LocalDate LocalDateTime OffsetDateTime Year YearMonth ZoneOffset]
    [java.time.temporal ChronoUnit]
    [clojure.lang PersistentArrayMap IObj])
  (:refer-clojure :exclude [comparator compile]))


(set! *warn-on-reflection* true)


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
  (fn [_ {:keys [type] :as expr}]
    (assert (string? type) (format "Missing :type in expression `%s`." (pr-str expr)))
    (keyword "elm.compiler.type" (str/kebab type))))


(defmethod compile* :default
  [_ {:keys [type]}]
  (throw (Exception. (str "Unsupported ELM expression type: " (or type "<missing>")))))


(defn compile
  "Compiles `expression` in `context`.

  Use `compile-library` to compile a whole library."
  [context expression]
  (compile* context expression))


(defn- compile-expression-def
  "Compiles the expression of `expression-def` in `context` and assocs the
  resulting compiled expression under :life/expression to the `expression-def`
  which itself is returned.

  Returns an anomaly on errors."
  {:arglists '([context expression-def])}
  [context {:keys [expression] :as expression-def}]
  (let [context (assoc context :eval-context (:context expression-def))]
    (try
      (assoc expression-def :life/expression (compile context expression))
      (catch Exception e
        (let [ex-data (ex-data e)]
          (if (::anom/category ex-data)
            (assoc ex-data
              :context context
              :elm/expression expression)
            {::anom/category ::anom/fault
             ::anom/message (ex-message e)
             :e e
             :context context
             :elm/expression expression}))))))


(defn compile-library
  "Compiles `library` using `node`.

  There are currently no options."
  [node library opts]
  (let [library (-> library
                    normalizer/normalize-library
                    equiv-relationships/find-equiv-rels-library
                    deps-infer/infer-library-deps
                    type-infer/infer-library-types)
        context (assoc opts :node node :library library)]
    (when-ok [expr-defs
              (transduce
                (map #(compile-expression-def context %))
                (completing
                  (fn [r {:keys [name] :life/keys [expression]
                          :as compiled-expression-def}]
                    (if (::anom/category compiled-expression-def)
                      (reduced compiled-expression-def)
                      (assoc r name expression))))
                {}
                (-> library :statements :def))]
      {:life/compiled-expression-defs expr-defs})))


(defn static? [x]
  (not (instance? blaze.elm.compiler.protocols.Expression x)))


(defn- record-name [s]
  (str (str/capital (str/camel (name s))) "OperatorExpression"))


(defmacro defunop
  {:arglists '([name attr-map? bindings & body])}
  [name & more]
  (let [attr-map (when (map? (first more)) (first more))
        more (if (map? (first more)) (next more) more)
        [[operand-binding expr-binding] & body] more]
    `(do
       ~(if expr-binding
          `(defrecord ~(symbol (record-name name)) [~'operand ~'expr]
             Expression
             (-eval [~'_ context# resource# scope#]
               (let [~operand-binding (-eval ~'operand context# resource# scope#)
                     ~expr-binding ~'expr]
                 ~@body)))
          `(defrecord ~(symbol (record-name name)) [~'operand]
             Expression
             (-eval [~'_ context# resource# scope#]
               (let [~operand-binding (-eval ~'operand context# resource# scope#)]
                 ~@body))))

       (defmethod compile* ~(keyword "elm.compiler.type" (clojure.core/name name))
         [context# ~'expr]
         (let [~'operand (compile (merge context# ~attr-map) (:operand ~'expr))]
           (if (static? ~'operand)
             (let [~operand-binding ~'operand
                   ~(or expr-binding '_) ~'expr]
               ~@body)
             ~(if expr-binding
                `(~(symbol (str "->" (record-name name))) ~'operand ~'expr)
                `(~(symbol (str "->" (record-name name))) ~'operand))))))))


(defmacro defbinop
  {:arglists '([name attr-map? bindings & body])}
  [name & more]
  (let [attr-map (when (map? (first more)) (first more))
        more (if (map? (first more)) (next more) more)
        [[op-1-binding op-2-binding] & body] more]
    `(do
       (defrecord ~(symbol (record-name name)) [~'operand-1 ~'operand-2]
         Expression
         (-eval [~'_ context# resource# scope#]
           (let [~op-1-binding (-eval ~'operand-1 context# resource# scope#)
                 ~op-2-binding (-eval ~'operand-2 context# resource# scope#)]
             ~@body)))

       (defmethod compile* ~(keyword "elm.compiler.type" (clojure.core/name name))
         [context# {[operand-1# operand-2#] :operand}]
         (let [context# (merge context# ~attr-map)
               operand-1# (compile context# operand-1#)
               operand-2# (compile context# operand-2#)]
           (if (and (static? operand-1#) (static? operand-2#))
             (let [~op-1-binding operand-1#
                   ~op-2-binding operand-2#]
               ~@body)
             (~(symbol (str "->" (record-name name))) operand-1# operand-2#)))))))


(defmacro defternop
  {:arglists '([name bindings & body])}
  [name [op-1-binding op-2-binding op-3-binding] & body]
  `(defmethod compile* ~(keyword "elm.compiler.type" (clojure.core/name name))
     [context# {[operand-1# operand-2# operand-3#] :operand}]
     (let [operand-1# (compile context# operand-1#)
           operand-2# (compile context# operand-2#)
           operand-3# (compile context# operand-3#)]
       (reify Expression
         (-eval [~'_ context# resource# scope#]
           (let [~op-1-binding (-eval operand-1# context# resource# scope#)
                 ~op-2-binding (-eval operand-2# context# resource# scope#)
                 ~op-3-binding (-eval operand-3# context# resource# scope#)]
             ~@body))))))


(defmacro defnaryop
  {:arglists '([name bindings & body])}
  [name [operands-binding] & body]
  `(do
     (defrecord ~(symbol (record-name name)) [~'operands]
       Expression
       (-eval [~'_ context# resource# scope#]
         (let [~operands-binding (mapv #(-eval % context# resource# scope#) ~'operands)]
           ~@body)))

     (defmethod compile* ~(keyword "elm.compiler.type" (clojure.core/name name))
       [context# {~'operands :operand}]
       (let [~'operands (mapv #(compile context# %) ~'operands)]
         (~(symbol (str "->" (record-name name))) ~'operands)))))


(defmacro defaggop
  {:arglists '([name bindings & body])}
  [name [source-binding] & body]
  `(defmethod compile* ~(keyword "elm.compiler.type" (clojure.core/name name))
     [{data-provider# :data-provider :as context#} {source# :source path# :path}]
     (let [source# (compile context# source#)
           path# (some-> path# (data-provider/compile-path data-provider#))]
       (if path#
         (reify Expression
           (-eval [~'_ context# resource# scope#]
             (let [source# (-eval source# context# resource# scope#)
                   ~source-binding (data-provider/resolve-property
                                     data-provider# source# path#)]
               ~@body)))
         (reify Expression
           (-eval [~'_ context# resource# scope#]
             (let [~source-binding (-eval source# context# resource# scope#)]
               ~@body)))))))


(defn- to-chrono-unit [precision]
  (case (str/lower precision)
    "year" ChronoUnit/YEARS
    "month" ChronoUnit/MONTHS
    "week" ChronoUnit/WEEKS
    "day" ChronoUnit/DAYS
    "hour" ChronoUnit/HOURS
    "minute" ChronoUnit/MINUTES
    "second" ChronoUnit/SECONDS
    "millisecond" ChronoUnit/MILLIS))


(defmacro defbinopp
  {:arglists '([name attr-map? bindings & body])}
  [name & more]
  (let [attr-map (when (map? (first more)) (first more))
        more (if (map? (first more)) (next more) more)
        [[op-1-binding op-2-binding precision-binding] & body] more]
    `(do
       (defrecord ~(symbol (record-name name)) [~'operand-1 ~'operand-2 ~'precision]
         Expression
         (-eval [~'_ context# resource# scope#]
           (let [~op-1-binding (-eval ~'operand-1 context# resource# scope#)
                 ~op-2-binding (-eval ~'operand-2 context# resource# scope#)
                 ~precision-binding ~'precision]
             ~@body)))

       (defmethod compile* ~(keyword "elm.compiler.type" (clojure.core/name name))
         [context# {[operand-1# operand-2#] :operand precision# :precision}]
         (let [context# (merge context# ~attr-map)
               operand-1# (compile context# operand-1#)
               operand-2# (compile context# operand-2#)
               precision# (some-> precision# to-chrono-unit)]
           (if (and (static? operand-1#) (static? operand-2#))
             (let [~op-1-binding operand-1#
                   ~op-2-binding operand-2#
                   ~precision-binding precision#]
               ~@body)
             (~(symbol (str "->" (record-name name)))
               operand-1# operand-2# precision#)))))))


(defmacro defunopp
  {:arglists '([name bindings & body])}
  [name [operand-binding precision-binding expr-binding] & body]
  `(defmethod compile* ~(keyword "elm.compiler.type" (clojure.core/name name))
     [context# {operand# :operand precision# :precision :as expr#}]
     (let [operand# (compile context# operand#)
           ~precision-binding (some-> precision# to-chrono-unit)
           ~(or expr-binding '_) expr#]
       (reify Expression
         (-eval [~'_ context# resource# scope#]
           (let [~operand-binding (-eval operand# context# resource# scope#)]
             ~@body))))))


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
(defrecord TupleExpression [elements]
  Expression
  (-eval [_ context resource scope]
    (reduce-kv
      (fn [r key value]
        (assoc r key (-eval value context resource scope)))
      {}
      elements)))


(extend-protocol p/StructuredType
  PersistentArrayMap
  (get [m key]
    (.valAt m key)))


(defn- compile-elements [context elements]
  (reduce
    (fn [r {:keys [name value]}]
      (assoc r (keyword name) (compile context value)))
    {}
    elements))


(defmethod compile* :elm.compiler.type/tuple
  [context {elements :element}]
  (->TupleExpression (compile-elements context elements)))


;; 2.2. Instance
(defmethod compile* :elm.compiler.type/instance
  [context {type :classType elements :element}]
  (let [elements (compile-elements context elements)]
    (when (every? static? (vals elements))
      (case type
        "{urn:hl7-org:elm-types:r1}Code"
        (let [{:keys [system version code]} elements]
          (code/to-code system version code))))))


;; 2.3. Property
(defn- fhir-type? [type]
  (and (keyword? type) (str/starts-with? (namespace type) "fhir")))


(defn- with-spec [spec x]
  (if (instance? IObj x)
    (with-meta x {:type spec})
    x))


(defn- search-choice-property [choices x]
  (loop [[[key spec] & more] choices]
    (if-let [val (get x key)]
      (with-spec spec val)
      (some-> more recur))))


(defn- get-fhir-property [key x]
  (if-let [child-spec (get (fhir-spec/child-specs (type x)) key)]
    (cond
      (fhir-spec/choice? child-spec)
      (search-choice-property (fhir-spec/choices child-spec) x)

      (= :many (fhir-spec/cardinality child-spec))
      (elm-util/eduction (map (partial with-spec (fhir-spec/type-spec child-spec))) (get x key))

      :else
      (when-let [val (get x key)]
        (with-spec child-spec val)))
    (throw-anom
      ::anom/incorrect
      (format "unknown FHIR data element `%s` in type `%s`" (name key) (name (type x))))))


(defn- get-property [key value]
  (cond
    (fhir-type? (type value))
    (get-fhir-property key value)

    :else
    (p/get value key)))


(defrecord SourcePropertyExpression [source key]
  Expression
  (-eval [_ context resource scope]
    (get-property key (-eval source context resource scope))))


(defrecord ChoiceFhirTypeSourcePropertyExpression [source key choices]
  Expression
  (-eval [_ context resource scope]
    (search-choice-property choices (-eval source context resource scope))))


(defrecord ManyFhirTypeSourcePropertyExpression [source key spec]
  Expression
  (-eval [_ context resource scope]
    (->> (get (-eval source context resource scope) key)
         (elm-util/eduction (map (partial with-spec spec))))))


(defrecord OneFhirTypeSourcePropertyExpression [source key spec]
  Expression
  (-eval [_ context resource scope]
    (when-let [val (get (-eval source context resource scope) key)]
      (with-spec spec val))))


(defrecord SingleScopePropertyExpression [key]
  Expression
  (-eval [_ _ _ value]
    (get-property key value)))


(defrecord ChoiceFhirTypeSingleScopePropertyExpression [key choices]
  Expression
  (-eval [_ _ _ value]
    (search-choice-property choices value)))


(defrecord ManyFhirTypeSingleScopePropertyExpression [key spec]
  Expression
  (-eval [_ _ _ value]
    (elm-util/eduction (map (partial with-spec spec)) (get value key))))


(defrecord OneFhirTypeSingleScopePropertyExpression [key spec]
  Expression
  (-eval [_ _ _ value]
    (when-let [val (get value key)]
      (with-spec spec val))))


(defrecord ScopePropertyExpression [scope-key key]
  Expression
  (-eval [_ _ _ scope]
    (get-property key (get scope scope-key))))


(defrecord ChoiceFhirTypeScopePropertyExpression [scope-key key choices]
  Expression
  (-eval [_ _ _ scope]
    (search-choice-property choices (get scope scope-key))))


(defrecord ManyFhirTypeScopePropertyExpression [scope-key key spec]
  Expression
  (-eval [_ _ _ scope]
    (elm-util/eduction (map (partial with-spec spec)) (get (get scope scope-key) key))))


(defrecord OneFhirTypeScopePropertyExpression [scope-key key spec]
  Expression
  (-eval [_ _ _ scope]
    (when-let [val (get (get scope scope-key) key)]
      (with-spec spec val))))


(defn- path->key [path]
  (let [[first-part & more-parts] (str/split path "." 2)]
    (when (and more-parts (not= ["value"] more-parts))
      (throw-anom ::anom/unsupported (format "Unsupported path `%s`with more than one part." path)))
    (keyword first-part)))


(defn- type-source-property-expr
  "Creates a source-property-expression with known FHIR `type`."
  [type source key]
  (if-let [child-spec (get (fhir-spec/child-specs type) key)]
    (cond
      (fhir-spec/choice? child-spec)
      (->ChoiceFhirTypeSourcePropertyExpression source key (fhir-spec/choices child-spec))

      (= :many (fhir-spec/cardinality child-spec))
      (->ManyFhirTypeSourcePropertyExpression source key (fhir-spec/type-spec child-spec))

      :else
      (->OneFhirTypeSourcePropertyExpression source key child-spec))
    (->SourcePropertyExpression source key)))


(defn- source-property-expr [source-type source key]
  (let [[ns name] (elm-util/parse-qualified-name source-type)]
    (if (= "http://hl7.org/fhir" ns)
      (type-source-property-expr (keyword "fhir" name) source key)
      (->SourcePropertyExpression source key))))


(defn- type-single-scope-property-expr [type key]
  (if-let [child-spec (get (fhir-spec/child-specs type) key)]
    (cond
      (fhir-spec/choice? child-spec)
      (->ChoiceFhirTypeSingleScopePropertyExpression key (fhir-spec/choices child-spec))

      (= :many (fhir-spec/cardinality child-spec))
      (->ManyFhirTypeSingleScopePropertyExpression key (fhir-spec/type-spec child-spec))

      :else
      (->OneFhirTypeSingleScopePropertyExpression key child-spec))
    (->SingleScopePropertyExpression key)))


(defn- single-scope-property-expr [source-type key]
  (let [[ns name] (elm-util/parse-qualified-name source-type)]
    (if (= "http://hl7.org/fhir" ns)
      (type-single-scope-property-expr (keyword "fhir" name) key)
      (->SingleScopePropertyExpression key))))


(defn- type-scope-property-expr [type scope key]
  (if-let [child-spec (get (fhir-spec/child-specs type) key)]
    (cond
      (fhir-spec/choice? child-spec)
      (->ChoiceFhirTypeScopePropertyExpression scope key (fhir-spec/choices child-spec))

      (= :many (fhir-spec/cardinality child-spec))
      (->ManyFhirTypeScopePropertyExpression scope key (fhir-spec/type-spec child-spec))

      :else
      (->OneFhirTypeScopePropertyExpression scope key child-spec))
    (->ScopePropertyExpression scope key)))


(defn- scope-property-expression [source-type scope key]
  (let [[ns name] (elm-util/parse-qualified-name source-type)]
    (if (= "http://hl7.org/fhir" ns)
      (type-scope-property-expr (keyword "fhir" name) scope key)
      (->ScopePropertyExpression scope key))))

(defmethod compile* :elm.compiler.type/property
  [{:life/keys [single-query-scope] :as context}
   {:keys [source scope path] :life/keys [source-type]}]
  (let [key (path->key path)]
    (cond
      source
      (source-property-expr source-type (compile context source) key)

      scope
      (if (= single-query-scope scope)
        (single-scope-property-expr source-type key)
        (scope-property-expression source-type scope key)))))



;; 3. Clinical Values

(defn- find-code-system-def
  "Returns the code-system-def with `name` from `library` or nil if not found."
  {:arglists '([library name])}
  [{{code-system-defs :def} :codeSystems} name]
  (some #(when (= name (:name %)) %) code-system-defs))


;; 3.1. Code
(defmethod compile* :elm.compiler.type/code
  [{:keys [library] :as context}
   {{system-name :name} :system :keys [code] :as expression}]
  ;; TODO: look into other libraries (:libraryName)
  (if-let [{system :id :keys [version]} (find-code-system-def library system-name)]
    (code/to-code system version code)
    (throw (ex-info (format "Can't find the code system `%s`." system-name)
                    {:context context
                     :expression expression}))))


;; 3.2. CodeDef
;;
;; Not needed because it's not an expression.


;; 3.3. CodeRef
(defn- find-code-def
  "Returns the code-def with `name` from `library` or nil if not found."
  {:arglists '([library name])}
  [{{code-defs :def} :codes} name]
  (some #(when (= name (:name %)) %) code-defs))


(defmethod compile* :elm.compiler.type/code-ref
  [{:keys [library] :as context} {:keys [name] :as expression}]
  ;; TODO: look into other libraries (:libraryName)
  (when-let [{code-system-ref :codeSystem code :id :as code-def}
             (find-code-def library name)]
    (if code-system-ref
      (when-let [{system :id :keys [version]} (compile context (assoc code-system-ref :type "CodeSystemRef"))]
        (code/to-code system version code))
      (throw (ex-info "Can't handle code-defs without code-system-ref."
                      {:context context
                       :expression expression
                       :code-def code-def})))))


;; 3.4. CodeSystemDef
;;
;; Not needed because it's not an expression.


;; 3.5. CodeSystemRef
(defmethod compile* :elm.compiler.type/code-system-ref
  [{:keys [library]} {:keys [name]}]
  ;; TODO: look into other libraries (:libraryName)
  (find-code-system-def library name))


;; 3.6. Concept
;;
;; TODO


;; 3.7. ConceptDef
;;
;; Not needed because it's not an expression.


;; 3.8. ConceptRef
;;
;; TODO


;; 3.9. Quantity
(defmethod compile* :elm.compiler.type/quantity
  [_ {:keys [value unit]}]
  (if unit
    (case unit
      ("year" "years") (date-time/period value 0 0)
      ("month" "months") (date-time/period 0 value 0)
      ("week" "weeks") (date-time/period 0 0 (* value 7 24 3600000))
      ("day" "days") (date-time/period 0 0 (* value 24 3600000))
      ("hour" "hours") (date-time/period 0 0 (* value 3600000))
      ("minute" "minutes") (date-time/period 0 0 (* value 60000))
      ("second" "seconds") (date-time/period 0 0 (* value 1000))
      ("millisecond" "milliseconds") (date-time/period 0 0 value)
      (quantity value unit))
    value))


;; 3.10. Ratio
;;
;; TODO

;; 3.11. ValueSetDef
;;
;; Not needed because it's not an expression.

;; 3.12. ValueSetRef
;;
;; TODO



;; 9. Reusing Logic

;; 9.2. ExpressionRef
;;
;; The ExpressionRef type defines an expression that references a previously
;; defined NamedExpression. The result of evaluating an ExpressionReference is
;; the result of evaluating the referenced NamedExpression.
(defn- find-expression-def
  "Returns the expression-def with `name` from `library` or nil if not found."
  {:arglists '([library name])}
  [{{expr-defs :def} :statements} name]
  (some #(when (= name (:name %)) %) expr-defs))


(defrecord ExpressionRef [name]
  Expression
  (-eval [_ {:keys [library-context] :as context} resource _]
    (let [expr (get library-context name ::not-found)]
      (if-not (identical? ::not-found expr)
        (-eval expr context resource nil)
        (throw-anom
          ::anom/incorrect
          (format "Expression `%s` not found." name)
          :context context)))))


(defmethod compile* :elm.compiler.type/expression-ref
  [{:keys [library eval-context] :as context}
   {:keys [name] def-eval-context :life/eval-context}]
  ;; TODO: look into other libraries (:libraryName)
  (when name
    (if-let [def (find-expression-def library name)]
      (cond
        (and (= "Unspecified" eval-context) (not= "Unspecified" def-eval-context))
        ;; The referenced expression has a concrete context but we are in the
        ;; Unspecified context. So we map the referenced expression over all
        ;; concrete resources.
        (reify Expression
          (-eval [_ {:keys [db library-context] :as context} _ _]
            (if-some [expression (get library-context name)]
              (mapv
                #(-eval expression context % nil)
                (d/list-resources db def-eval-context))
              (throw-anom
                ::anom/incorrect
                (format "Expression `%s` not found." name)
                :context context))))

        :else
        (if-let [result-type-name (:resultTypeName def)]
          (vary-meta (->ExpressionRef name) assoc :result-type-name result-type-name)
          (->ExpressionRef name)))
      (throw-anom
        ::anom/incorrect
        (format "Expression `%s` not found." name)
        :context context))))


;; 9.4. FunctionRef
(defmethod compile* :elm.compiler.type/function-ref
  [context {:keys [name] operands :operand}]
  ;; TODO: look into other libraries (:libraryName)
  (let [operands (mapv #(compile context %) operands)]
    (case name
      "ToQuantity"
      (function/->ToQuantityFunctionExpression (first operands))

      "ToDate"
      (function/->ToDateFunctionExpression (first operands))

      "ToDateTime"
      (function/->ToDateTimeFunctionExpression (first operands))

      "ToString"
      (function/->ToStringFunctionExpression (first operands))

      "ToCode"
      (function/->ToCodeFunctionExpression (first operands))

      "ToDecimal"
      (first operands)

      (throw (Exception. (str "Unsupported function `" name "` in `FunctionRef` expression."))))))



;; 10. Queries
(declare compile-with-equiv-clause)

;; 10.1. Query
;;
;; The Query operator represents a clause-based query. The result of the query
;; is determined by the type of sources included, as well as the clauses used in
;; the query.
(defmulti compile-sort-by-item (fn [_ {:keys [type]}] type))


(defmethod compile-sort-by-item "ByExpression"
  [context sort-by-item]
  (update sort-by-item :expression #(compile context %)))


(defmethod compile-sort-by-item :default
  [_ sort-by-item]
  sort-by-item)


(defmethod compile* :elm.compiler.type/query
  [{:keys [optimizations] :as context}
   {sources :source
    relationships :relationship
    :keys [where]
    {return :expression :keys [distinct] :or {distinct true}} :return
    {sort-by-items :by} :sort
    :as expr}]
  (when (seq (filter (comp #{"With"} :type) relationships))
    (throw-anom
      ::anom/unsupported
      "Unsupported With clause in query expression."
      :expression expr))
  (when (seq (filter (comp #{"Without"} :type) relationships))
    (throw-anom
      ::anom/unsupported
      "Unsupported Without clause in query expression."
      :expression expr))
  (if (= 1 (count sources))
    (let [{:keys [expression alias]} (first sources)
          context (dissoc context :optimizations)
          source (compile context expression)
          context (assoc context :life/single-query-scope alias)
          with-equiv-clauses (filter (comp #{"WithEquiv"} :type) relationships)
          with-xform-factories (map #(compile-with-equiv-clause context %) with-equiv-clauses)
          where-xform-factory (some->> where (compile context) (query/where-xform-factory))
          distinct (if (contains? optimizations :non-distinct) false distinct)
          return-xform-factory (query/return-xform-factory (some->> return (compile context)) distinct)
          xform-factory (query/xform-factory with-xform-factories where-xform-factory return-xform-factory)
          sort-by-items (mapv #(compile-sort-by-item context %) sort-by-items)]
      (if (empty? sort-by-items)
        (if xform-factory
          (if (contains? optimizations :first)
            (query/eduction-expr xform-factory source)
            (query/into-vector-expr xform-factory source))
          source)
        (if xform-factory
          (query/xform-sort-expr xform-factory source (first sort-by-items))
          (query/sort-expr source (first sort-by-items)))))
    (throw (Exception. (str "Unsupported number of " (count sources) " sources in query.")))))


(defmethod compile* :elm.compiler.type/identifier-ref
  [_ {:keys [name]}]
  (->SingleScopePropertyExpression (keyword name)))


;; 10.3. AliasRef
;;
;; The AliasRef expression allows for the reference of a specific source within
;; the context of a query.
(defrecord AliasRefExpression [key]
  Expression
  (-eval [_ _ _ scopes]
    (get scopes key)))


(defrecord SingleScopeAliasRefExpression []
  Expression
  (-eval [_ _ _ scope]
    scope))


(def single-scope-alias-ref-expression (->SingleScopeAliasRefExpression))


(defmethod compile* :elm.compiler.type/alias-ref
  [{:life/keys [single-query-scope]} {:keys [name]}]
  (if (= single-query-scope name)
    single-scope-alias-ref-expression
    (->AliasRefExpression name)))


;; 10.12. With
;;
;; The With clause restricts the elements of a given source to only those
;; elements that have elements in the related source that satisfy the suchThat
;; condition. This operation is known as a semi-join in database languages.
(defn- find-operand-with-alias
  "Finds the operand in `expression` that accesses entities with `alias`."
  [operands alias]
  (some #(when (contains? (:life/scopes %) alias) %) operands))


(defn compile-with-equiv-clause
  "We use the terms `lhs` and `rhs` for left-hand-side and right-hand-side of
  the semi-join here.

  Returns an XformFactory."
  {:arglists '([context with-equiv-clause])}
  [context {:keys [alias] rhs :expression equiv-operands :equivOperand
            such-that :suchThat}]
  (if-let [single-query-scope (:life/single-query-scope context)]
    (if-let [rhs-operand (find-operand-with-alias equiv-operands alias)]
      (if-let [lhs-operand (find-operand-with-alias equiv-operands
                                                    single-query-scope)]
        (let [rhs (compile context rhs)
              rhs-operand (compile (assoc context :life/single-query-scope alias)
                                   rhs-operand)
              lhs-operand (compile context lhs-operand)
              such-that (some->> such-that
                                 (compile (dissoc context :life/single-query-scope)))]
          (query/->WithXformFactory rhs rhs-operand such-that lhs-operand
                                    single-query-scope))
        (throw-anom
          ::anom/incorrect
          (format "Unsupported call without left-hand-side operand.")))
      (throw-anom
        ::anom/incorrect
        (format "Unsupported call without right-hand-side operand with alias `%s`." alias)))
    (throw-anom
      ::anom/incorrect
      (format "Unsupported call without single query scope."))))


;; TODO 10.13. Without



;; 11. External Data

;; 11.1. Retrieve
(defmethod compile* :elm.compiler.type/retrieve
  [{:keys [node eval-context] :as context}
   {context-expr :context
    data-type :dataType
    code-property :codeProperty
    codes-expr :codes
    :or {code-property "code"}}]
  (let [[type-ns data-type] (elm-util/parse-qualified-name data-type)]
    (if (= "http://hl7.org/fhir" type-ns)
      (if-let [related-context-expr (some->> context-expr (compile context))]
        (retrieve/with-related-context-expr
          node
          related-context-expr
          data-type
          code-property
          (some->> codes-expr (compile context)))
        (retrieve/expr
          node
          eval-context
          data-type
          code-property
          (some->> codes-expr (compile context))))
      (throw-anom
        ::anom/unsupported
        (format "Unsupported type namespace `%s` in Retrieve expression." type-ns)
        :type-ns type-ns))))



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

;; static-a is either true or nil but not false
(defrecord StaticAndOperatorExpression [static-a b]
  Expression
  (-eval [_ context resource scope]
    (let [b (-eval b context resource scope)]
      (cond
        (false? b) false
        (and (true? static-a) (true? b)) true))))


(defn- and-static [static-a b]
  (if (static? b)
    (cond
      (false? b) false
      (and (true? static-a) (true? b)) true)
    (->StaticAndOperatorExpression static-a b)))


(defrecord AndOperatorExpression [a b]
  Expression
  (-eval [_ context resource scope]
    (let [a (-eval a context resource scope)]
      (if (false? a)
        false
        (let [b (-eval b context resource scope)]
          (cond
            (false? b) false
            (and (true? a) (true? b)) true))))))


(defmethod compile* :elm.compiler.type/and
  [context {[a b] :operand}]
  (let [a (compile context a)]
    (if (static? a)
      (if (false? a)
        false
        (and-static a (compile context b)))
      (let [b (compile context b)]
        (if (static? b)
          (if (false? b)
            false
            (and-static b a))
          (->AndOperatorExpression a b))))))


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

;; static-a is either false or nil but not true
(defrecord StaticOrOperatorExpression [static-a b]
  Expression
  (-eval [_ context resource scope]
    (let [b (-eval b context resource scope)]
      (cond
        (true? b) true
        (and (false? static-a) (false? b)) false))))


(defn- or-static [static-a b]
  (if (static? b)
    (cond
      (true? b) true
      (and (false? static-a) (false? b)) false)
    (->StaticOrOperatorExpression static-a b)))


(defrecord OrOperatorExpression [a b]
  Expression
  (-eval [_ context resource scope]
    (let [a (-eval a context resource scope)]
      (if (true? a)
        true
        (let [b (-eval b context resource scope)]
          (cond
            (true? b) true
            (and (false? a) (false? b)) false))))))


(defmethod compile* :elm.compiler.type/or
  [context {[a b] :operand}]
  (let [a (compile context a)]
    (if (static? a)
      (if (true? a)
        true
        (or-static a (compile context b)))
      (let [operand-2 (compile context b)]
        (if (static? operand-2)
          (if (true? operand-2)
            true
            (or-static operand-2 a))
          (->OrOperatorExpression a operand-2))))))


;; 13.5 Xor
(defrecord StaticXOrOperatorExpression [static-a b]
  Expression
  (-eval [_ context resource scope]
    (let [b (-eval b context resource scope)]
      (cond
        (or (and (true? static-a) (true? b)) (and (false? static-a) (false? b))) false
        (or (and (true? static-a) (false? b)) (and (false? static-a) (true? b))) true))))


(defn- xor-static [static-a b]
  (if (static? b)
    (cond
      (or (and (true? static-a) (true? b)) (and (false? static-a) (false? b))) false
      (or (and (true? static-a) (false? b)) (and (false? static-a) (true? b))) true)
    (->StaticXOrOperatorExpression static-a b)))


(defrecord XOrOperatorExpression [a b]
  Expression
  (-eval [_ context resource scope]
    (let [a (-eval a context resource scope)]
      (if (nil? a)
        nil
        (let [b (-eval b context resource scope)]
          (cond
            (or (and (true? a) (true? b)) (and (false? a) (false? b))) false
            (or (and (true? a) (false? b)) (and (false? a) (true? b))) true))))))


(defmethod compile* :elm.compiler.type/xor
  [context {[a b] :operand}]
  (let [a (compile context a)]
    (if (static? a)
      (if (nil? a)
        nil
        (xor-static a (compile context b)))
      (let [b (compile context b)]
        (if (static? b)
          (if (nil? b)
            nil
            (xor-static b a))
          (->XOrOperatorExpression a b))))))



;; 14. Nullological Operators

;; 14.1. Null
(defmethod compile* :elm.compiler.type/null
  [_ _])


;; 14.2. Coalesce
;;
;; The Coalesce operator returns the first non-null result in a list of
;; arguments. If all arguments evaluate to null, the result is null. The static
;; type of the first argument determines the type of the result, and all
;; subsequent arguments must be of that same type.
;;
;; TODO: The list type argument is missing in the doc.
(defmethod compile* :elm.compiler.type/coalesce
  [context {operands :operand}]
  (condp = (count operands)
    1
    (let [operand (first operands)]
      (if (= "List" (:type operand))
        (let [operand (compile context operand)]
          (reify Expression
            (-eval [_ context resource scope]
              (reduce
                (fn [_ elem]
                  (let [elem (-eval elem context resource scope)]
                    (when (some? elem)
                      (reduced elem))))
                nil
                (-eval operand context resource scope)))))
        (let [operand (compile context operand)]
          (reify Expression
            (-eval [_ context resource scope]
              (-eval operand context resource scope))))))
    (let [operands (mapv #(compile context %) operands)]
      (reify Expression
        (-eval [_ context resource scope]
          (reduce
            (fn [_ operand]
              (let [operand (-eval operand context resource scope)]
                (when (some? operand)
                  (reduced operand))))
            nil
            operands))))))


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
(defrecord ComparandCaseExpression [comparand items else]
  Expression
  (-eval [_ context resource scope]
    (let [comparand (-eval comparand context resource scope)]
      (loop [[{:keys [when then]} & next-items] items]
        (if (p/equal comparand (-eval when context resource scope))
          (-eval then context resource scope)
          (if (empty? next-items)
            (-eval else context resource scope)
            (recur next-items)))))))


(defrecord MultiConditionalCaseExpression [items else]
  Expression
  (-eval [_ context resource scope]
    (loop [[{:keys [when then]} & next-items] items]
      (if (-eval when context resource scope)
        (-eval then context resource scope)
        (if (empty? next-items)
          (-eval else context resource scope)
          (recur next-items))))))


(defmethod compile* :elm.compiler.type/case
  [context {:keys [comparand else] items :caseItem}]
  (let [comparand (some->> comparand (compile context))
        items
        (mapv
          (fn [{:keys [when then]}]
            {:when (compile context when)
             :then (compile context then)})
          items)
        else (compile context else)]
    (if comparand
      (->ComparandCaseExpression comparand items else)
      (->MultiConditionalCaseExpression items else))))


;; 15.2. If
(defrecord IfExpression [condition then else]
  Expression
  (-eval [_ context resource scope]
    (if (-eval condition context resource scope)
      (-eval then context resource scope)
      (-eval else context resource scope))))


(defmethod compile* :elm.compiler.type/if
  [context {:keys [condition then else]}]
  (let [condition (compile context condition)]
    (cond
      (true? condition) (compile context then)
      (or (false? condition) (nil? condition)) (compile context else)
      :else (->IfExpression condition (compile context then) (compile context else)))))



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
(defrecord RoundOperatorExpression [operand precision]
  Expression
  (-eval [_ context resource scope]
    (p/round (-eval operand context resource scope)
             (-eval precision context resource scope))))


(defmethod compile* :elm.compiler.type/round
  [context {:keys [operand precision]}]
  (let [operand (compile context operand)
        precision (or (some->> precision (compile context)) 0)]
    (if (and (static? operand) (static? precision))
      (p/round operand precision)
      (->RoundOperatorExpression operand precision))))


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



;; 17. String Operators

;; 17.1. Combine
(defmethod compile* :elm.compiler.type/combine
  [context {:keys [source separator]}]
  (let [source (compile context source)
        separator (some->> separator (compile context))]
    (if separator
      (reify Expression
        (-eval [_ context resource scope]
          (when-let [source (-eval source context resource scope)]
            (string/combine (-eval separator context resource scope) source))))
      (reify Expression
        (-eval [_ context resource scope]
          (when-let [source (-eval source context resource scope)]
            (string/combine source)))))))


;; 17.2. Concatenate
(defnaryop concatenate [strings]
  (string/combine strings))


;; 17.3. EndsWith
(defbinop ends-with [s suffix]
  (when (and s suffix)
    (str/ends-with? s suffix)))


;; 17.6. Indexer
(defbinop indexer [x index]
  (p/indexer x index))


;; 17.7. LastPositionOf
(defmethod compile* :elm.compiler.type/last-position-of
  [context {:keys [pattern string]}]
  (let [pattern (compile context pattern)
        string (compile context string)]
    (reify Expression
      (-eval [_ context resource scope]
        (when-let [^String pattern (-eval pattern context resource scope)]
          (when-let [^String string (-eval string context resource scope)]
            (.lastIndexOf string pattern)))))))


;; 17.8. Length
(defunop length [x]
  (count x))


;; 17.9. Lower
(defunop lower [s]
  (str/lower s))


;; 17.10. Matches
(defbinop matches [s pattern]
  (when (and s pattern)
    (some? (re-matches (re-pattern pattern) s))))


;; 17.12. PositionOf
(defmethod compile* :elm.compiler.type/position-of
  [context {:keys [pattern string]}]
  (let [pattern (compile context pattern)
        string (compile context string)]
    (reify Expression
      (-eval [_ context resource scope]
        (when-let [^String pattern (-eval pattern context resource scope)]
          (when-let [^String string (-eval string context resource scope)]
            (.indexOf string pattern)))))))


;; 17.13. ReplaceMatches
(defternop replace-matches [s pattern substitution]
  (when (and s pattern substitution)
    (str/replace s (re-pattern pattern) substitution)))


;; 17.14. Split
(defmethod compile* :elm.compiler.type/split
  [context {string :stringToSplit :keys [separator]}]
  (let [string (compile context string)
        separator (some->> separator (compile context))]
    (if separator
      (reify Expression
        (-eval [_ context resource scope]
          (when-let [string (-eval string context resource scope)]
            (if (= "" string)
              [string]
              (if-let [separator (-eval separator context resource scope)]
                (condp = (count separator)
                  0
                  [string]
                  1
                  (loop [[char & more] string
                         result []
                         acc (StringBuilder.)]
                    (if (= (str char) separator)
                      (if more
                        (recur more (conj result (str acc)) (StringBuilder.))
                        (conj result (str acc)))
                      (if more
                        (recur more result (.append acc char))
                        (conj result (str (.append acc char))))))
                  ;; TODO: implement split with more than one char.
                  (throw (Exception. "TODO: implement split with separators longer than one char.")))
                [string])))))
      (reify Expression
        (-eval [_ context resource scope]
          (when-let [string (-eval string context resource scope)]
            [string]))))))


;; 17.16. StartsWith
(defbinop starts-with [s prefix]
  (when (and s prefix)
    (str/starts-with? s prefix)))


;; 17.17. Substring
(defmethod compile* :elm.compiler.type/substring
  [context {string :stringToSub start-index :startIndex :keys [length]}]
  (let [string (compile context string)
        start-index (compile context start-index)
        length (some->> length (compile context))]
    (if length
      (reify Expression
        (-eval [_ context resource scope]
          (when-let [^String string (-eval string context resource scope)]
            (when-let [start-index (-eval start-index context resource scope)]
              (when (and (<= 0 start-index) (< start-index (count string)))
                (subs string start-index (min (+ start-index length)
                                              (count string))))))))
      (reify Expression
        (-eval [_ context resource scope]
          (when-let [^String string (-eval string context resource scope)]
            (when-let [start-index (-eval start-index context resource scope)]
              (when (and (<= 0 start-index) (< start-index (count string)))
                (subs string start-index)))))))))


;; 17.18. Upper
(defunop upper [s]
  (str/upper s))



;; 18. Date and Time Operators

(defn- check-year-range [year]
  (when-not (< 0 year 10000)
    (throw-anom ::anom/incorrect (format "Year `%d` out of range." year))))


(defn- to-year [year]
  (check-year-range year)
  (Year/of year))


(defn- to-year-month [year month]
  (check-year-range year)
  (YearMonth/of ^long year ^long month))


(defn- to-local-date [year month day]
  (check-year-range year)
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


(defrecord YearExpression [year]
  Expression
  (-eval [_ context resource scope]
    (some-> (-eval year context resource scope) to-year)))


(defrecord YearMonthExpression [year month]
  Expression
  (-eval [_ context resource scope]
    (when-let [year (-eval year context resource scope)]
      (if-let [month (-eval month context resource scope)]
        (to-year-month year month)
        (to-year year)))))


(defrecord LocalDateExpression [year month day]
  Expression
  (-eval [_ context resource scope]
    (when-let [year (-eval year context resource scope)]
      (if-let [month (-eval month context resource scope)]
        (if-let [day (-eval day context resource scope)]
          (to-local-date year month day)
          (to-year-month year month))
        (to-year year)))))


;; 18.6. Date
(defmethod compile* :elm.compiler.type/date
  [context {:keys [year month day]}]
  (let [year (some->> year (compile context))
        month (some->> month (compile context))
        day (some->> day (compile context))]
    (cond
      (and (int? day) (int? month) (int? year))
      (to-local-date year month day)

      (some? day)
      (->LocalDateExpression year month day)

      (and (int? month) (int? year))
      (to-year-month year month)

      (some? month)
      (->YearMonthExpression year month)

      (int? year)
      (to-year year)

      :else
      (some-> year ->YearExpression))))


;; 18.7. DateFrom
(defunop date-from [x]
  (p/date-from x))


;; 18.8. DateTime
(defmethod compile* :elm.compiler.type/date-time
  [context {:keys [year month day hour minute second millisecond]
            timezone-offset :timezoneOffset
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
          (-eval [_ {:keys [now]} _ _]
            (to-local-date-time-with-offset
              now year month day hour minute second millisecond timezone-offset)))

        (some? hour)
        (reify Expression
          (-eval [_ {:keys [now] :as context} resource scope]
            (to-local-date-time-with-offset
              now
              (-eval year context resource scope)
              (-eval month context resource scope)
              (-eval day context resource scope)
              (-eval hour context resource scope)
              (or (-eval minute context resource scope) 0)
              (or (-eval second context resource scope) 0)
              (or (-eval millisecond context resource scope) 0)
              timezone-offset)))

        :else
        (throw (ex-info "Need at least an hour if timezone offset is given."
                        {:expression expression})))

      (some? timezone-offset)
      (cond
        (some? hour)
        (reify Expression
          (-eval [_ {:keys [now] :as context} resource scope]
            (to-local-date-time-with-offset
              now
              (-eval year context resource scope)
              (-eval month context resource scope)
              (-eval day context resource scope)
              (-eval hour context resource scope)
              (or (-eval minute context resource scope) 0)
              (or (-eval second context resource scope) 0)
              (or (-eval millisecond context resource scope) 0)
              (-eval timezone-offset context resource scope))))

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
          (-eval [_ context resource scope]
            (to-local-date-time
              (-eval year context resource scope)
              (-eval month context resource scope)
              (-eval day context resource scope)
              (-eval hour context resource scope)
              (or (-eval minute context resource scope) 0)
              (or (-eval second context resource scope) 0)
              (or (-eval millisecond context resource scope) 0))))

        (and (int? day) (int? month) (int? year))
        (to-local-date year month day)

        (some? day)
        (->LocalDateExpression year month day)

        (and (int? month) (int? year))
        (to-year-month year month)

        (some? month)
        (->YearMonthExpression year month)

        (int? year)
        (to-year year)

        :else
        (some-> year ->YearExpression)))))


;; 18.9. DateTimeComponentFrom
(defunopp date-time-component-from [x precision]
  (p/date-time-component-from x precision))


;; 18.10. DifferenceBetween
(defbinopp difference-between [operand-1 operand-2 precision]
  (p/difference-between operand-1 operand-2 precision))


;; 18.11. DurationBetween
(defbinopp duration-between [operand-1 operand-2 precision]
  (p/duration-between operand-1 operand-2 precision))


;; 18.13. Now
(defrecord NowExpression []
  Expression
  (-eval [_ {:keys [now]} _ _]
    now))


(def now-expression (->NowExpression))


(defmethod compile* :elm.compiler.type/now [_ _]
  now-expression)


;; 18.14. SameAs
(defbinopp same-as [x y precision]
  (p/same-as x y precision))


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
        (-eval [_ context resource scope]
          (local-time (-eval hour context resource scope)
                      (-eval minute context resource scope)
                      (-eval second context resource scope)
                      (-eval millisecond context resource scope))))

      (and (int? second) (int? minute) (int? hour))
      (local-time hour minute second)

      (some? second)
      (reify Expression
        (-eval [_ context resource scope]
          (local-time (-eval hour context resource scope)
                      (-eval minute context resource scope)
                      (-eval second context resource scope))))

      (and (int? minute) (int? hour))
      (local-time hour minute)

      (some? minute)
      (reify Expression
        (-eval [_ context resource scope]
          (local-time (-eval hour context resource scope)
                      (-eval minute context resource scope))))

      (int? hour)
      (local-time hour)

      :else
      (reify Expression
        (-eval [_ context resource scope]
          (local-time (-eval hour context resource scope)))))))


(defrecord TimeOfDayExpression []
  Expression
  (-eval [_ {:keys [now]} _ _]
    (.toLocalTime ^OffsetDateTime now)))


(def ^:private time-of-day-expr
  (->TimeOfDayExpression))


;; 18.21. TimeOfDay
(defmethod compile* :elm.compiler.type/time-of-day
  [_ _]
  time-of-day-expr)


(defrecord TodayExpression []
  Expression
  (-eval [_ {:keys [now]} _ _]
    (.toLocalDate ^OffsetDateTime now)))


(def ^:private today-expr
  (->TodayExpression))


;; 18.22. Today
(defmethod compile* :elm.compiler.type/today
  [_ _]
  today-expr)



;; 19. Interval Operators

;; 19.1. Interval
(defn- determine-type [{:keys [resultTypeName asType]}]
  (or resultTypeName asType))


(defrecord IntervalExpression
  [type low high low-closed-expression high-closed-expression low-closed high-closed]
  Expression
  (-eval [_ context resource scope]
    (let [low (-eval low context resource scope)
          high (-eval high context resource scope)
          low-closed (or (-eval low-closed-expression context resource scope) low-closed)
          high-closed (or (-eval high-closed-expression context resource scope) high-closed)]
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
          (p/predecessor high))))))


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
    (if (and (static? low)
             (static? high)
             (static? low-closed-expression)
             (static? high-closed-expression))
      (let [low-closed (or low-closed-expression low-closed)
            high-closed (or high-closed-expression high-closed)]
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
            (p/predecessor high))))
      (->IntervalExpression type low high low-closed-expression
                            high-closed-expression low-closed high-closed))))


;; 19.2. After
(defbinopp after [operand-1 operand-2 precision]
  (p/after operand-1 operand-2 precision))


;; 19.3. Before
(defbinopp before [operand-1 operand-2 precision]
  (p/before operand-1 operand-2 precision))


;; 19.4. Collapse
(defbinop collapse [source _]
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
(defbinopp contains
  {:optimizations #{:first :non-distinct}}
  [list-or-interval x precision]
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
(defbinop intersect [a b]
  (p/intersect a b))


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
(defbinop union [a b]
  (p/union a b))


;; 19.32. Width
(defunop width [{:keys [start end]}]
  (p/subtract end start))



;; 20. List Operators

;; 20.1. List
(defrecord ListOperatorExpression [elements]
  Expression
  (-eval [_ context resource scope]
    (mapv #(-eval % context resource scope) elements)))


(defmethod compile* :elm.compiler.type/list
  [context {elements :element}]
  (let [elements (mapv #(compile context %) elements)]
    (if (every? static? elements)
      elements
      (->ListOperatorExpression elements))))


;; 20.3. Current
(defmethod compile* :elm.compiler.type/current
  [_ {:keys [scope]}]
  (if scope
    (reify Expression
      (-eval [_ _ _ scopes]
        (get scopes scope)))
    (reify Expression
      (-eval [_ _ _ scope]
        scope))))


;; 20.4. Distinct
;;
;; TODO: implementation is O(n^2)
(defunop distinct [list]
  (when list
    (reduce
      (fn [result x]
        (if (p/contains result x nil)
          result
          (conj result x)))
      []
      list)))


;; 20.8. Exists
(defunop exists
  {:optimizations #{:first :non-distinct}}
  [list]
  (not (d/ri-empty? list)))


;; 20.9. Filter
(defmethod compile* :elm.compiler.type/filter
  [context {:keys [source condition scope]}]
  (let [source (compile context source)
        condition (compile context condition)]
    (if scope
      (reify Expression
        (-eval [_ context resource scopes]
          (when-let [source (-eval source context resource scopes)]
            (filterv
              (fn [x]
                (-eval condition context resource (assoc scopes scope x)))
              source))))
      (reify Expression
        (-eval [_ context resource scopes]
          (when-let [source (-eval source context resource scopes)]
            (filterv
              (fn [_]
                (-eval condition context resource scopes))
              source)))))))


;; 20.10. First
;;
;; TODO: orderBy
(defrecord FirstExpression [source]
  Expression
  (-eval [_ context resource scopes]
    (d/ri-first (-eval source context resource scopes))))


(defmethod compile* :elm.compiler.type/first
  [context {:keys [source]}]
  (let [source (compile (assoc context :optimizations #{:first :non-distinct}) source)]
    (if (static? source)
      (first source)
      (->FirstExpression source))))


;; 20.11. Flatten
(defunop flatten [list]
  (when list
    (letfn [(flatten [to from]
              (reduce
                (fn [result x]
                  (if (sequential? x)
                    (flatten result x)
                    (conj result x)))
                to
                from))]
      (flatten [] list))))


;; 20.12. ForEach
(defmethod compile* :elm.compiler.type/for-each
  [context {:keys [source element scope]}]
  (let [source (compile context source)
        element (compile context element)]
    (if scope
      (reify Expression
        (-eval [_ context resource scopes]
          (when-let [source (-eval source context resource scopes)]
            (mapv
              (fn [x]
                (-eval element context resource (assoc scopes scope x)))
              source))))
      (reify Expression
        (-eval [_ context resource scopes]
          (when-let [source (-eval source context resource scopes)]
            (mapv
              (fn [_]
                (-eval element context resource scopes))
              source)))))))


;; 20.16. IndexOf
(defmethod compile* :elm.compiler.type/index-of
  [context {:keys [source element]}]
  (let [source (compile context source)
        element (compile context element)]
    (reify Expression
      (-eval [_ context resource scopes]
        (when-let [source (-eval source context resource scopes)]
          (when-let [element (-eval element context resource scopes)]
            (or
              (first
                (keep-indexed
                  (fn [idx x]
                    (when
                      (p/equal element x)
                      idx))
                  source))
              -1)))))))


;; 20.18. Last
;;
;; TODO: orderBy
(defmethod compile* :elm.compiler.type/last
  [context {:keys [source]}]
  (let [source (compile context source)]
    (reify Expression
      (-eval [_ context resource scopes]
        (peek (-eval source context resource scopes))))))


;; 20.24. Repeat
;;
;; TODO: not implemented


;; 20.25. SingletonFrom
(defunop singleton-from [list {{:keys [locator]} :operand :as expression}]
  (try
    (p/singleton-from list)
    (catch Exception _
      (throw (ex-info (append-locator "More than one element in expression `SingletonFrom` at" locator)
                      {:expression expression})))))


;; 20.26. Slice
(defmethod compile* :elm.compiler.type/slice
  [context {:keys [source] start-index :startIndex end-index :endIndex}]
  (let [source (compile context source)
        start-index (some->> start-index (compile context))
        end-index (some->> end-index (compile context))]
    (reify Expression
      (-eval [_ context resource scopes]
        (when-let [source (-eval source context resource scopes)]
          (let [start-index (or (-eval start-index context resource scopes) 0)
                end-index (or (-eval end-index context resource scopes) (count source))]
            (if (or (neg? start-index) (< end-index start-index))
              []
              (subvec
                source
                start-index
                end-index))))))))


;; 20.27. Sort
(defmethod compile* :elm.compiler.type/sort
  [context {:keys [source] sort-by-items :by}]
  (let [source (compile context source)
        sort-by-items (mapv #(compile-sort-by-item context %) sort-by-items)]
    (reduce
      (fn [source {:keys [type direction]}]
        (case type
          "ByDirection"
          (let [comp (query/comparator direction)]
            (reify Expression
              (-eval [_ context resource scopes]
                (when-let [source (-eval source context resource scopes)]
                  (sort-by identity comp source)))))))
      source
      sort-by-items)))



;; 21. Aggregate Operators

;; 21.1. AllTrue
(defaggop all-true [source]
  (reduce #(if (false? %2) (reduced %2) %1) true source))


;; 21.2. AnyTrue
(defaggop any-true [source]
  (reduce #(if (true? %2) (reduced %2) %1) false source))


;; 21.3. Avg
(defaggop avg [source]
  (transduce identity aggregates/avg-reducer source))


;; 21.4. Count
(defaggop count [source]
  (transduce identity aggregates/count-reducer source))


;; 21.5. GeometricMean
(defaggop geometric-mean [source]
  (transduce identity aggregates/geometric-mean-reducer source))


;; 21.6. Product
(defaggop product [source]
  (transduce identity aggregates/product-reducer source))


;; 21.7. Max
(defaggop max [source]
  (transduce identity aggregates/max-reducer source))


;; 21.8. Median
(defaggop median [source]
  (let [sorted (vec (sort-by identity query/asc-comparator (remove nil? source)))]
    (when (seq sorted)
      (if (zero? (rem (count sorted) 2))
        (let [upper-idx (quot (count sorted) 2)]
          (p/divide (p/add (nth sorted (dec upper-idx))
                           (nth sorted upper-idx))
                    2))
        (nth sorted (quot (count sorted) 2))))))


;; 21.9. Min
(defaggop min [source]
  (transduce identity aggregates/min-reducer source))


;; 21.10. Mode
(defaggop mode [source]
  (transduce identity aggregates/max-freq-reducer (frequencies (remove nil? source))))


;; 21.11. PopulationVariance
(defaggop population-variance [source]
  (transduce identity aggregates/population-variance-reducer source))


;; 21.12. PopulationStdDev
(defaggop population-std-dev [source]
  (p/power (transduce identity aggregates/population-variance-reducer source) 0.5M))


;; 21.13. Sum
(defaggop sum [source]
  (transduce identity aggregates/sum-reducer source))


;; 21.14. StdDev
(defaggop std-dev [source]
  (p/power (transduce identity aggregates/variance-reducer source) 0.5M))


;; 21.15. Variance
(defaggop variance [source]
  (transduce identity aggregates/variance-reducer source))



;; 22. Type Operators

;; 22.1. As
;;
;; The As operator allows the result of an expression to be cast as a given
;; target type. This allows expressions to be written that are statically typed
;; against the expected run-time type of the argument. If the argument is not of
;; the specified type, and the strict attribute is false (the default), the
;; result is null. If the argument is not of the specified type and the strict
;; attribute is true, an exception is thrown.
(defrecord AsExpression [operand matches-type?]
  Expression
  (-eval [_ context resource scope]
    (let [value (-eval operand context resource scope)]
      (when (matches-type? value)
        value))))


(defn- matches-elm-named-type-fn [type-name]
  (case type-name
    "Boolean" boolean?
    "Integer" int?
    "DateTime" temporal?
    "Quantity" quantity?
    (throw-anom
      ::anom/unsupported
      (format "Unsupported ELM type `%s` in As expression." type-name)
      :type-name type-name)))


(defn- matches-named-type-fn [type-name]
  (let [[type-ns type-name] (elm-util/parse-qualified-name type-name)]
    (case type-ns
      "http://hl7.org/fhir"
      (if (Character/isUpperCase ^char (first type-name))
        (comp #(identical? (keyword "fhir" type-name) %) type)
        (case type-name
          "boolean" boolean?
          "integer" int?
          "string" string?
          "decimal" number?
          "uri" string?
          "url" string?
          "canonical" string?
          "dateTime" string?
          (throw-anom
            ::anom/unsupported
            (format "Unsupported FHIR type `%s` in As expression." type-name)
            :type-name type-name)))
      "urn:hl7-org:elm-types:r1"
      (matches-elm-named-type-fn type-name)
      (throw-anom
        ::anom/unsupported
        (format "Unsupported type namespace `%s` in As expression." type-ns)
        :type-ns type-ns))))


(defn- matches-type-specifier-fn [as-type-specifier]
  (case (:type as-type-specifier)
    "NamedTypeSpecifier"
    (matches-named-type-fn (:name as-type-specifier))

    "ListTypeSpecifier"
    (let [pred (matches-type-specifier-fn (:elementType as-type-specifier))]
      (fn matches-type? [x]
        (every? pred x)))

    (throw-anom
      ::anom/unsupported
      (format "Unsupported type specifier type `%s` in As expression."
              (:type as-type-specifier))
      :type-specifier-type (:type as-type-specifier))))


(defn- matches-type-fn
  [{as-type :asType as-type-specifier :asTypeSpecifier :as expression}]
  (cond
    as-type
    (matches-named-type-fn as-type)

    as-type-specifier
    (matches-type-specifier-fn as-type-specifier)

    :else
    (throw-anom
      ::anom/fault
      "Invalid As expression without `as-type` and `as-type-specifier`."
      :expression expression)))


(defmethod compile* :elm.compiler.type/as
  [context {:keys [operand] :as expression}]
  (when-some [operand (compile context operand)]
    (->AsExpression operand (matches-type-fn expression))))


;; 22.2. CanConvert

;; 22.3. CanConvertQuantity


;; 22.4. Children
(defrecord ChildrenOperatorExpression [source]
  Expression
  (-eval [_ context resource scope]
    (p/children (-eval source context resource scope))))


(defmethod compile* :elm.compiler.type/children
  [context {:keys [source]}]
  (when-let [source (compile context source)]
    (->ChildrenOperatorExpression source)))


;; 22.5. Convert


;; 22.6. ConvertQuantity
(defbinop convert-quantity [x unit]
  (p/convert-quantity x unit))


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
(defrecord DescendentsOperatorExpression [source]
  Expression
  (-eval [_ context resource scope]
    (p/descendents (-eval source context resource scope))))


(defmethod compile* :elm.compiler.type/descendents
  [context {:keys [source]}]
  (when-let [source (compile context source)]
    (->DescendentsOperatorExpression source)))


;; 22.17. Is

;; 22.18. ToBoolean

;; 22.19. ToChars

;; 22.20. ToConcept

;; 22.21. ToDate
(defrecord ToDateOperatorExpression [operand]
  Expression
  (-eval [_ {:keys [now] :as context} resource scope]
    (p/to-date (-eval operand context resource scope) now)))


(defmethod compile* :elm.compiler.type/to-date
  [context {:keys [operand]}]
  (when-let [operand (compile context operand)]
    (->ToDateOperatorExpression operand)))


;; 22.22. ToDateTime
(defrecord ToDateTimeOperatorExpression [operand]
  Expression
  (-eval [_ {:keys [now] :as context} resource scope]
    (p/to-date-time (-eval operand context resource scope) now)))


(defmethod compile* :elm.compiler.type/to-date-time
  [context {:keys [operand]}]
  (when-let [operand (compile context operand)]
    (->ToDateTimeOperatorExpression operand)))


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


;; 22.28. ToString
(defunop to-string [x]
  (p/to-string x))



;; 23. Clinical Operators

;; 23.3. CalculateAge
;;
;; see normalizer.clj


;; 23.4. CalculateAgeAt
(defrecord CalculateAgeAtExpression [birth-date date precision]
  Expression
  (-eval [_ {:keys [now] :as context} resource scope]
    (p/duration-between
      (p/to-date (-eval birth-date context resource scope) now)
      (-eval date context resource scope)
      precision)))


(defmethod compile* :elm.compiler.type/calculate-age-at
  [context {[birth-date date] :operand precision :precision}]
  (when-let [birth-date (compile context birth-date)]
    (when-let [date (compile context date)]
      (->CalculateAgeAtExpression
        birth-date
        date
        (some-> precision to-chrono-unit)))))
