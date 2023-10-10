(ns blaze.elm.compiler.type-operators
  "22. Type Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.anomaly :as ba :refer [throw-anom]]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.macros :refer [defbinop defunop]]
    [blaze.elm.date-time :as date-time]
    [blaze.elm.protocols :as p]
    [blaze.elm.quantity :as quantity]
    [blaze.elm.util :as elm-util]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type.system :as system]))


(set! *warn-on-reflection* true)


;; 22.1. As
(defn- matches-elm-named-type-fn [type-name]
  (case type-name
    "Boolean" ['elm/boolean boolean?]
    "Integer" ['elm/integer int?]
    "DateTime" ['elm/date-time date-time/temporal?]
    "Quantity" ['elm/quantity quantity/quantity?]
    (throw-anom
      (ba/unsupported
        (format "Unsupported ELM type `%s` in As expression." type-name)
        :type-name type-name))))


(defn- matches-named-type-fn [type-name]
  (let [[type-ns type-name] (elm-util/parse-qualified-name type-name)]
    (case type-ns
      "http://hl7.org/fhir"
      [(symbol "fhir" type-name) (comp #{(keyword "fhir" type-name)} fhir-spec/fhir-type)]
      "urn:hl7-org:elm-types:r1"
      (matches-elm-named-type-fn type-name)
      (throw-anom
        (ba/unsupported
          (format "Unsupported type namespace `%s` in As expression." type-ns)
          :type-ns type-ns)))))


(defn- matches-type-specifier-fn [as-type-specifier]
  (case (:type as-type-specifier)
    "NamedTypeSpecifier"
    (matches-named-type-fn (:name as-type-specifier))

    "ListTypeSpecifier"
    (let [[type pred] (matches-type-specifier-fn (:elementType as-type-specifier))]
      [`(~'list ~type)
       (fn matches-type? [x]
         (every? pred x))])

    (throw-anom
      (ba/unsupported
        (format "Unsupported type specifier type `%s` in As expression."
                (:type as-type-specifier))
        :type-specifier-type (:type as-type-specifier)))))


(defn- matches-type-fn
  [{as-type :asType as-type-specifier :asTypeSpecifier :as expression}]
  (cond
    as-type
    (matches-named-type-fn as-type)

    as-type-specifier
    (matches-type-specifier-fn as-type-specifier)

    :else
    (throw-anom
      (ba/fault
        "Invalid As expression without `as-type` and `as-type-specifier`."
        :expression expression))))


(defn as-op [type pred operand]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (as-op type pred (core/-attach-cache operand cache)))
    (-resolve-refs [_ expression-defs]
      (as-op type pred (core/-resolve-refs operand expression-defs)))
    (-resolve-params [_ parameters]
      (as-op type pred (core/-resolve-params operand parameters)))
    (-eval [_ context resource scope]
      (let [value (core/-eval operand context resource scope)]
        (when (pred value)
          value)))
    (-form [_]
      `(~'as ~type ~(core/-form operand)))))


(defmethod core/compile* :elm.compiler.type/as
  [context {:keys [operand] :as expression}]
  (when-some [operand (core/compile* context operand)]
    (let [[type pred] (matches-type-fn expression)]
      (as-op type pred operand))))


;; TODO 22.2. CanConvert


;; 22.3. CanConvertQuantity
(defbinop can-convert-quantity [x unit]
  (p/can-convert-quantity x unit))


;; 22.4. Children
(defn children-op [source]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (children-op (core/-attach-cache source cache)))
    (-resolve-refs [_ expression-defs]
      (children-op (core/-resolve-refs source expression-defs)))
    (-resolve-params [_ parameters]
      (children-op (core/-resolve-params source parameters)))
    (-eval [_ context resource scope]
      (p/children (core/-eval source context resource scope)))
    (-form [_]
      (list 'children (core/-form source)))))

(defmethod core/compile* :elm.compiler.type/children
  [context {:keys [source]}]
  (some-> (core/compile* context source) children-op))


;; TODO 22.5. Convert


;; 22.6. ConvertQuantity
(defbinop convert-quantity [x unit]
  (p/convert-quantity x unit))


;; 22.7. ConvertsToBoolean
(defunop converts-to-boolean [operand]
  (when (some? operand)
    (some? (p/to-boolean operand))))


;; 22.8. ConvertsToDate
(defn- converts-to-date-op [operand]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (converts-to-date-op (core/-attach-cache operand cache)))
    (-resolve-refs [_ expression-defs]
      (converts-to-date-op (core/-resolve-refs operand expression-defs)))
    (-resolve-params [_ parameters]
      (converts-to-date-op (core/-resolve-params operand parameters)))
    (-eval [_ {:keys [now] :as context} resource scope]
      (when-let [operand (core/-eval operand context resource scope)]
        (when (some? operand)
          (some? (p/to-date operand now)))))
    (-form [_]
      (list 'converts-to-date (core/-form operand)))))


(defmethod core/compile* :elm.compiler.type/converts-to-date
  [context {:keys [operand]}]
  (some-> (core/compile* context operand) converts-to-date-op))


;; 22.9. ConvertsToDateTime
(defn converts-to-date-time-op [operand]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (converts-to-date-time-op (core/-attach-cache operand cache)))
    (-resolve-refs [_ expression-defs]
      (converts-to-date-time-op (core/-resolve-refs operand expression-defs)))
    (-resolve-params [_ parameters]
      (converts-to-date-time-op (core/-resolve-params operand parameters)))
    (-eval [_ {:keys [now] :as context} resource scope]
      (when-let [operand (core/-eval operand context resource scope)]
        (when (some? operand)
          (some? (p/to-date-time operand now)))))
    (-form [_]
      (list 'converts-to-date-time (core/-form operand)))))


(defmethod core/compile* :elm.compiler.type/converts-to-date-time
  [context {:keys [operand]}]
  (when-let [operand (core/compile* context operand)]
    (if (system/date? operand)
      (some? (p/to-date-time operand nil))
      (converts-to-date-time-op operand))))


;; 22.10. ConvertsToDecimal
(defunop converts-to-decimal [operand]
  (when (some? operand)
    (some? (p/to-decimal operand))))


;; 22.11. ConvertsToLong
(defunop converts-to-long [operand]
  (when (some? operand)
    (some? (p/to-long operand))))


;; 22.12. ConvertsToInteger
(defunop converts-to-integer [operand]
  (when (some? operand)
    (some? (p/to-integer operand))))


;; 22.13. ConvertsToQuantity
(defunop converts-to-quantity [operand]
  (when (some? operand)
    (some? (p/to-quantity operand))))


;; 22.14. ConvertsToRatio
(defunop converts-to-ratio [operand]
  (when (some? operand)
    (some? (p/to-ratio operand))))


;; 22.15. ConvertsToString
(defunop converts-to-string [operand]
  (when (some? operand)
    (some? (p/to-string operand))))


;; 22.16. ConvertsToTime
(defn- converts-to-time-op [operand]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (converts-to-time-op (core/-attach-cache operand cache)))
    (-resolve-refs [_ expression-defs]
      (converts-to-time-op (core/-resolve-refs operand expression-defs)))
    (-resolve-params [_ parameters]
      (converts-to-time-op (core/-resolve-params operand parameters)))
    (-eval [_ {:keys [now] :as context} resource scope]
      (when-some [operand (core/-eval operand context resource scope)]
        (some? (p/to-time operand now))))
    (-form [_]
      (list 'converts-to-time (core/-form operand)))))


(defmethod core/compile* :elm.compiler.type/converts-to-time
  [context {:keys [operand]}]
  (some-> (core/compile* context operand) converts-to-time-op))


;; 22.17. Descendents
(defn- descendents-op [source]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (descendents-op (core/-attach-cache source cache)))
    (-resolve-refs [_ expression-defs]
      (descendents-op (core/-resolve-refs source expression-defs)))
    (-resolve-params [_ parameters]
      (descendents-op (core/-resolve-params source parameters)))
    (-eval [_ context resource scope]
      (p/descendents (core/-eval source context resource scope)))
    (-form [_]
      (list 'descendents (core/-form source)))))


(defmethod core/compile* :elm.compiler.type/descendents
  [context {:keys [source]}]
  (some-> (core/compile* context source) descendents-op))


;; 22.18. Is
(defn- matches-elm-named-type-is [type-name]
  (case type-name
    "Boolean" ['elm/boolean boolean?]
    "Integer" ['elm/integer int?]
    "Long" ['elm/long system/long?]
    "Decimal" ['elm/decimal decimal?]
    "Date" ['elm/date system/date?]
    "DateTime" ['elm/date-time date-time/temporal?]
    "Quantity" ['elm/quantity quantity/quantity?]
    "String" ['elm/string string?]))


(defn- matches-named-type-is [type-name]
  (let [[type-ns type-name] (elm-util/parse-qualified-name type-name)]
    (case type-ns
      "http://hl7.org/fhir"
      [(symbol "fhir" type-name)
       (let [fhir-type (keyword "fhir" type-name)]
         #(identical? fhir-type (fhir-spec/fhir-type %)))]
      "urn:hl7-org:elm-types:r1"
      (matches-elm-named-type-is type-name))))


(defn- matches-type-specifier-is [is-type-specifier]
  (case (:type is-type-specifier)
    "NamedTypeSpecifier"
    (matches-named-type-is (:name is-type-specifier))

    "ListTypeSpecifier"
    (let [[type pred] (matches-type-specifier-is (:elementType is-type-specifier))]
      [`(~'list ~type)
       (fn matches-type? [x]
         (every? pred x))])))


(defn- matches-type-is
  [{is-type :isType is-type-specifier :isTypeSpecifier}]
  (cond
    is-type
    (matches-named-type-is is-type)

    is-type-specifier
    (matches-type-specifier-is is-type-specifier)))


(defn is-op [type pred operand]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (is-op type pred (core/-attach-cache operand cache)))
    (-resolve-refs [_ expression-defs]
      (is-op type pred (core/-resolve-refs operand expression-defs)))
    (-resolve-params [_ parameters]
      (is-op type pred (core/-resolve-params operand parameters)))
    (-eval [_ context resource scope]
      (pred (core/-eval operand context resource scope)))
    (-form [_]
      `(~'is ~type ~(core/-form operand)))))


(defmethod core/compile* :elm.compiler.type/is
  [context {:keys [operand] :as expression}]
  (let [[type pred] (matches-type-is expression)]
    (is-op type pred (core/compile* context operand))))


;; 22.19. ToBoolean
(defunop to-boolean [x]
  (p/to-boolean x))


;; 22.20. ToChars
(defunop to-chars [operand]
  (when (string? operand)
    (map str operand)))


;; 22.21. ToConcept
(defunop to-concept [x]
  (p/to-concept x))


;; 22.22. ToDate
(defn to-date-op [operand]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (to-date-op (core/-attach-cache operand cache)))
    (-resolve-refs [_ expression-defs]
      (to-date-op (core/-resolve-refs operand expression-defs)))
    (-resolve-params [_ parameters]
      (to-date-op (core/-resolve-params operand parameters)))
    (-eval [_ {:keys [now] :as context} resource scope]
      (p/to-date (core/-eval operand context resource scope) now))
    (-form [_]
      (list 'to-date (core/-form operand)))))

(defmethod core/compile* :elm.compiler.type/to-date
  [context {:keys [operand]}]
  (when-let [operand (core/compile* context operand)]
    (if (system/date? operand)
      (p/to-date operand nil)
      (to-date-op operand))))


;; 22.23. ToDateTime
(defn to-date-time-op [operand]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (to-date-time-op (core/-attach-cache operand cache)))
    (-resolve-refs [_ expression-defs]
      (to-date-time-op (core/-resolve-refs operand expression-defs)))
    (-resolve-params [_ parameters]
      (to-date-time-op (core/-resolve-params operand parameters)))
    (-eval [_ {:keys [now] :as context} resource scope]
      (p/to-date-time (core/-eval operand context resource scope) now))
    (-form [_]
      (list 'to-date-time (core/-form operand)))))

(defmethod core/compile* :elm.compiler.type/to-date-time
  [context {:keys [operand]}]
  (when-let [operand (core/compile* context operand)]
    (if (system/date? operand)
      (p/to-date-time operand nil)
      (to-date-time-op operand))))


;; 22.24. ToDecimal
(defunop to-decimal [x]
  (p/to-decimal x))


;; 22.25. ToInteger
(defunop to-integer [x]
  (p/to-integer x))


;; 22.26. ToList
(defunop to-list [x]
  (if (nil? x) [] [x]))


;; 22.27. ToLong
(defunop to-long [x]
  (p/to-long x))


;; 22.28. ToQuantity
(defunop to-quantity [x]
  (p/to-quantity x))


;; 22.29. ToRatio
(defunop to-ratio [x]
  (p/to-ratio x))


;; 22.30. ToString
(defunop to-string [x]
  (p/to-string x))


;; 22.31. ToTime
(defn- to-time-op [operand]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (to-time-op (core/-attach-cache operand cache)))
    (-resolve-refs [_ expression-defs]
      (to-time-op (core/-resolve-refs operand expression-defs)))
    (-resolve-params [_ parameters]
      (to-time-op (core/-resolve-params operand parameters)))
    (-eval [_ {:keys [now] :as context} resource scope]
      (p/to-time (core/-eval operand context resource scope) now))
    (-form [_]
      (list 'to-time (core/-form operand)))))


(defmethod core/compile* :elm.compiler.type/to-time
  [context {:keys [operand]}]
  (some-> (core/compile* context operand) to-time-op))
