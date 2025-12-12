(ns blaze.elm.compiler.reusing-logic
  "9. Reusing Logic

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.anomaly :as ba :refer [throw-anom]]
   [blaze.db.api :as d]
   [blaze.elm.code :as code]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.macros :refer [reify-expr]]
   [blaze.elm.concept :refer [concept]]
   [blaze.elm.interval :as interval]
   [blaze.elm.protocols :as p]
   [blaze.elm.quantity :as quantity]
   [blaze.fhir.spec :as fhir-spec])
  (:import
   [blaze.fhir.spec.type Period Quantity]))

(set! *warn-on-reflection* true)

;; 9.2. ExpressionRef
;;
;; The ExpressionRef type defines an expression that references a previously
;; defined NamedExpression. The result of evaluating an ExpressionReference is
;; the result of evaluating the referenced NamedExpression.
(defn- expression-not-found-anom [context name]
  (ba/incorrect (format "Expression `%s` not found." name) :context context))

(defn- expr-ref [name]
  (reify-expr core/Expression
    (-resolve-refs [expr expression-defs]
      (or (:expression (get expression-defs name)) expr))
    (-eval [_ {:keys [expression-defs] :as context} resource _]
      (if-let [{:keys [expression]} (get expression-defs name)]
        (core/-eval expression context resource nil)
        (throw-anom (expression-not-found-anom context name))))
    (-form [_]
      (list 'expr-ref name))))

(defn- find-def
  "Returns the def with `name` from `library` or nil if not found."
  {:arglists '([library name])}
  [{{defs :def} :statements} name]
  (some #(when (= name (:name %)) %) defs))

(defn- find-expression-def [library name]
  (when-let [def (find-def library name)]
    (when (= "ExpressionDef" (:type def))
      def)))

(defn- expression-def-not-found-anom [context name]
  (ba/incorrect
   (format "Expression definition `%s` not found." name)
   :context context))

(defmethod core/compile* :elm.compiler.type/expression-ref
  [{:keys [library eval-context] :as context}
   {:keys [name] def-eval-context :life/eval-context}]
  ;; TODO: look into other libraries (:libraryName)
  (when name
    (if-let [def (find-expression-def library name)]
      (cond
        (and (= "Unfiltered" eval-context) (not= "Unfiltered" def-eval-context))
        ;; The referenced expression has a concrete context but we are in the
        ;; Unfiltered context. So we map the referenced expression over all
        ;; concrete resources.
        (reify-expr core/Expression
          (-eval [_ {:keys [db expression-defs] :as context} _ _]
            (if-some [{:keys [expression]} (get expression-defs name)]
              (mapv
               #(core/-eval expression context % nil)
               (d/type-list db def-eval-context))
              (throw-anom (expression-def-not-found-anom context name)))))

        :else
        (if-let [result-type-name (:resultTypeName def)]
          (vary-meta (expr-ref name) assoc :result-type-name result-type-name)
          (expr-ref name)))
      (throw-anom (expression-def-not-found-anom context name)))))

(defprotocol ToQuantity
  (-to-quantity [x]))

(extend-protocol ToQuantity
  Quantity
  (-to-quantity [fhir-quantity]
    (when-let [decimal (-> fhir-quantity :value :value)]
      (quantity/quantity decimal (or (-> fhir-quantity :code :value) "1"))))

  Object
  (-to-quantity [x]
    (throw-anom (ba/fault (format "Can't convert `%s` to quantity." x))))

  nil
  (-to-quantity [_]))

(defn- to-quantity-function-expr [operand]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper to-quantity-function-expr cache operand))
    (-resolve-refs [_ expression-defs]
      (to-quantity-function-expr (core/-resolve-refs operand expression-defs)))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper to-quantity-function-expr parameters operand))
    (-optimize [_ db]
      (core/optimize-helper to-quantity-function-expr db operand))
    (-eval [_ context resource scope]
      (-to-quantity (core/-eval operand context resource scope)))
    (-form [_]
      `(~'call "ToQuantity" ~(core/-form operand)))))

(defn- to-code [{:keys [system version code]}]
  (code/code (:value system) (:value version) (:value code)))

(defn- to-code-function-expr [operand]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper to-code-function-expr cache operand))
    (-resolve-refs [_ expression-defs]
      (to-code-function-expr (core/-resolve-refs operand expression-defs)))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper to-code-function-expr parameters operand))
    (-optimize [_ db]
      (core/optimize-helper to-code-function-expr db operand))
    (-eval [_ context resource scope]
      (some-> (core/-eval operand context resource scope) to-code))
    (-form [_]
      `(~'call "ToCode" ~(core/-form operand)))))

(defn- to-decimal-function-expr [operand]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper to-decimal-function-expr cache operand))
    (-resolve-refs [_ expression-defs]
      (to-decimal-function-expr (core/-resolve-refs operand expression-defs)))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper to-decimal-function-expr parameters operand))
    (-optimize [_ db]
      (core/optimize-helper to-decimal-function-expr db operand))
    (-eval [_ context resource scope]
      (:value (core/-eval operand context resource scope)))
    (-form [_]
      `(~'call "ToDecimal" ~(core/-form operand)))))

(defn- to-date-function-expr [operand]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper to-date-function-expr cache operand))
    (-resolve-refs [_ expression-defs]
      (to-date-function-expr (core/-resolve-refs operand expression-defs)))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper to-date-function-expr parameters operand))
    (-optimize [_ db]
      (core/optimize-helper to-date-function-expr db operand))
    (-eval [_ context resource scope]
      (:value (core/-eval operand context resource scope)))
    (-form [_]
      `(~'call "ToDate" ~(core/-form operand)))))

(defn- to-date-time-function-expr [operand]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper to-date-time-function-expr cache operand))
    (-resolve-refs [_ expression-defs]
      (to-date-time-function-expr (core/-resolve-refs operand expression-defs)))
    (-resolve-params [_ parameters]
      (to-date-time-function-expr (core/-resolve-params operand parameters)))
    (-optimize [_ parameters]
      (to-date-time-function-expr (core/-optimize operand parameters)))
    (-eval [_ {:keys [now] :as context} resource scope]
      (p/to-date-time (:value (core/-eval operand context resource scope)) now))
    (-form [_]
      `(~'call "ToDateTime" ~(core/-form operand)))))

(defn- to-string-function-expr [operand]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper to-string-function-expr cache operand))
    (-resolve-refs [_ expression-defs]
      (to-string-function-expr (core/-resolve-refs operand expression-defs)))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper to-string-function-expr parameters operand))
    (-optimize [_ db]
      (core/optimize-helper to-string-function-expr db operand))
    (-eval [_ context resource scope]
      (let [value (core/-eval operand context resource scope)]
        (some-> (cond-> value (fhir-spec/primitive-val? value) :value) str)))
    (-form [_]
      `(~'call "ToString" ~(core/-form operand)))))

(defprotocol ToInterval
  (-to-interval [x context]))

(extend-protocol ToInterval
  Period
  (-to-interval [{:keys [start end]} {:keys [now]}]
    (interval/interval
     (p/to-date-time (:value start) now)
     (p/to-date-time (:value end) now)))

  nil
  (-to-interval [_ _]))

(defn- to-interval-function-expr [operand]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper to-interval-function-expr cache operand))
    (-resolve-refs [_ expression-defs]
      (to-interval-function-expr (core/-resolve-refs operand expression-defs)))
    (-resolve-params [_ parameters]
      (to-interval-function-expr (core/-resolve-params operand parameters)))
    (-optimize [_ parameters]
      (to-interval-function-expr (core/-optimize operand parameters)))
    (-eval [_ context resource scope]
      (-to-interval (core/-eval operand context resource scope) context))
    (-form [_]
      `(~'call "ToInterval" ~(core/-form operand)))))

(defn- to-concept-function-expr [operand]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper to-concept-function-expr cache operand))
    (-resolve-refs [_ expression-defs]
      (to-concept-function-expr (core/-resolve-refs operand expression-defs)))
    (-resolve-params [_ parameters]
      (to-concept-function-expr (core/-resolve-params operand parameters)))
    (-optimize [_ parameters]
      (to-concept-function-expr (core/-optimize operand parameters)))
    (-eval [_ context resource scope]
      (when-let [{:keys [coding]} (core/-eval operand context resource scope)]
        (concept (mapv to-code coding))))
    (-form [_]
      `(~'call "ToConcept" ~(core/-form operand)))))

;; 9.4. FunctionRef
(defmethod core/compile* :elm.compiler.type/function-ref
  [context {:keys [name] operands :operand}]
  ;; TODO: look into other libraries (:libraryName)
  (let [operands (map #(core/compile* context %) operands)]
    (case name
      "ToQuantity"
      (to-quantity-function-expr (first operands))

      "ToDate"
      (to-date-function-expr (first operands))

      "ToDateTime"
      (to-date-time-function-expr (first operands))

      "ToString"
      (to-string-function-expr (first operands))

      "ToCode"
      (to-code-function-expr (first operands))

      "ToDecimal"
      (to-decimal-function-expr (first operands))

      "ToInterval"
      (to-interval-function-expr (first operands))

      "ToConcept"
      (to-concept-function-expr (first operands))

      (ba/throw-when (core/compile-function context name operands)))))

;; 9.5 OperandRef
(defmethod core/compile* :elm.compiler.type/operand-ref
  [_ {:keys [name]}]
  (reify-expr core/Expression
    (-eval [_ _ _ scope]
      (scope name))
    (-form [_]
      `(~'operand-ref ~name))))
