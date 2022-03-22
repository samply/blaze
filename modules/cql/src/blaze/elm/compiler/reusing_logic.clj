(ns blaze.elm.compiler.reusing-logic
  "9. Reusing Logic

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.anomaly :as ba :refer [throw-anom]]
    [blaze.db.api :as d]
    [blaze.elm.code :as code]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.interval :as interval]
    [blaze.elm.protocols :as p]
    [blaze.elm.quantity :as quantity]
    [blaze.fhir.spec.type :as type])
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


(defrecord ExpressionRef [name]
  core/Expression
  (-eval [_ {:keys [library-context] :as context} resource _]
    (let [expr (get library-context name ::not-found)]
      (if (identical? ::not-found expr)
        (throw-anom (expression-not-found-anom context name))
        (core/-eval expr context resource nil))))
  (-form [_]
    `(~'expr-ref ~name)))


(defn- find-expression-def
  "Returns the expression-def with `name` from `library` or nil if not found."
  {:arglists '([library name])}
  [{{expr-defs :def} :statements} name]
  (some #(when (= name (:name %)) %) expr-defs))


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
        (reify core/Expression
          (-eval [_ {:keys [db library-context] :as context} _ _]
            (if-some [expression (get library-context name)]
              (mapv
                #(core/-eval expression context % nil)
                (d/type-list db def-eval-context))
              (throw-anom (expression-def-not-found-anom context name)))))

        :else
        (if-let [result-type-name (:resultTypeName def)]
          (vary-meta (->ExpressionRef name) assoc :result-type-name result-type-name)
          (->ExpressionRef name)))
      (throw-anom (expression-def-not-found-anom context name)))))


(defprotocol ToQuantity
  (-to-quantity [x]))


(extend-protocol ToQuantity
  Quantity
  (-to-quantity [m]
    (when-let [value (.-value m)]
      (quantity/quantity value (or (-> (.-code m) type/value) "1"))))

  Object
  (-to-quantity [x]
    (throw-anom (ba/incorrect (format "Can't convert `%s` to quantity." x))))

  nil
  (-to-quantity [_]))


(defrecord ToQuantityFunctionExpression [operand]
  core/Expression
  (-eval [_ context resource scope]
    (-to-quantity (core/-eval operand context resource scope)))
  (-form [_]
    `(~'call "ToQuantity" ~(core/-form operand))))


(defrecord ToCodeFunctionExpression [operand]
  core/Expression
  (-eval [_ context resource scope]
    (let [{:keys [system version code]} (core/-eval operand context resource scope)]
      (code/to-code (type/value system) (type/value version) (type/value code)))))


(defrecord ToDateFunctionExpression [operand]
  core/Expression
  (-eval [_ {:keys [now] :as context} resource scope]
    (p/to-date (core/-eval operand context resource scope) now)))


(defrecord ToDateTimeFunctionExpression [operand]
  core/Expression
  (-eval [_ {:keys [now] :as context} resource scope]
    (p/to-date-time (core/-eval operand context resource scope) now))
  (-form [_]
    `(~'call "ToDateTime" ~(core/-form operand))))


(defrecord ToStringFunctionExpression [operand]
  core/Expression
  (-eval [_ context resource scope]
    (str (core/-eval operand context resource scope)))
  (-form [_]
    `(~'call "ToString" ~(core/-form operand))))


(defprotocol ToInterval
  (-to-interval [x context]))


(extend-protocol ToInterval
  Period
  (-to-interval [{:keys [start end]} {:keys [now]}]
    (interval/interval
      (p/to-date-time (type/value start) now)
      (p/to-date-time (type/value end) now)))

  nil
  (-to-interval [_ _]))


(defrecord ToIntervalFunctionExpression [operand]
  core/Expression
  (-eval [_ context resource scope]
    (-to-interval (core/-eval operand context resource scope) context))
  (-form [_]
    `(~'call "ToInterval" ~(core/-form operand))))


;; 9.4. FunctionRef
(defmethod core/compile* :elm.compiler.type/function-ref
  [context {:keys [name] operands :operand}]
  ;; TODO: look into other libraries (:libraryName)
  (let [operands (map #(core/compile* context %) operands)]
    (case name
      "ToQuantity"
      (->ToQuantityFunctionExpression (first operands))

      "ToDate"
      (->ToDateFunctionExpression (first operands))

      "ToDateTime"
      (->ToDateTimeFunctionExpression (first operands))

      "ToString"
      (->ToStringFunctionExpression (first operands))

      "ToCode"
      (->ToCodeFunctionExpression (first operands))

      "ToDecimal"
      (first operands)

      "ToInterval"
      (->ToIntervalFunctionExpression (first operands))

      (throw (Exception. (str "Unsupported function `" name "` in `FunctionRef` expression."))))))
