(ns blaze.elm.compiler.type-operators
  "22. Type Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.anomaly :as ba :refer [throw-anom]]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.macros :refer [defbinop defunop reify-expr]]
   [blaze.elm.date-time :as date-time]
   [blaze.elm.protocols :as p]
   [blaze.elm.quantity :as quantity]
   [blaze.elm.util :as elm-util]
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
      [(symbol "fhir" type-name) (comp #{(keyword "fhir" type-name)} :fhir/type)]
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
  (reify-expr core/Expression
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
(defunop children
  {:operand-key :source}
  [source]
  (p/children source))

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
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper converts-to-date-op cache operand))
    (-resolve-refs [_ expression-defs]
      (core/resolve-refs-helper converts-to-date-op expression-defs operand))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper converts-to-date-op parameters operand))
    (-optimize [_ db]
      (core/optimize-helper converts-to-date-op db operand))
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
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper converts-to-date-time-op cache operand))
    (-resolve-refs [_ expression-defs]
      (core/resolve-refs-helper converts-to-date-time-op expression-defs operand))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper converts-to-date-time-op parameters operand))
    (-optimize [_ db]
      (core/optimize-helper converts-to-date-time-op db operand))
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
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper converts-to-time-op cache operand))
    (-resolve-refs [_ expression-defs]
      (core/resolve-refs-helper converts-to-time-op expression-defs operand))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper converts-to-time-op parameters operand))
    (-optimize [_ db]
      (core/optimize-helper converts-to-time-op db operand))
    (-eval [_ {:keys [now] :as context} resource scope]
      (when-some [operand (core/-eval operand context resource scope)]
        (some? (p/to-time operand now))))
    (-form [_]
      (list 'converts-to-time (core/-form operand)))))

(defmethod core/compile* :elm.compiler.type/converts-to-time
  [context {:keys [operand]}]
  (some-> (core/compile* context operand) converts-to-time-op))

;; 22.17. Descendents
(defunop descendents
  {:operand-key :source}
  [source]
  (p/descendents source))

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
         #(identical? fhir-type (:fhir/type %)))]
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
  (reify-expr core/Expression
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
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper to-date-op cache operand))
    (-resolve-refs [_ expression-defs]
      (core/resolve-refs-helper to-date-op expression-defs operand))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper to-date-op parameters operand))
    (-optimize [_ db]
      (core/optimize-helper to-date-op db operand))
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
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper to-date-time-op cache operand))
    (-resolve-refs [_ expression-defs]
      (core/resolve-refs-helper to-date-time-op expression-defs operand))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper to-date-time-op parameters operand))
    (-optimize [_ db]
      (core/optimize-helper to-date-time-op db operand))
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
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper to-time-op cache operand))
    (-resolve-refs [_ expression-defs]
      (core/resolve-refs-helper to-time-op expression-defs operand))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper to-time-op parameters operand))
    (-optimize [_ db]
      (core/optimize-helper to-time-op db operand))
    (-eval [_ {:keys [now] :as context} resource scope]
      (p/to-time (core/-eval operand context resource scope) now))
    (-form [_]
      (list 'to-time (core/-form operand)))))

(defmethod core/compile* :elm.compiler.type/to-time
  [context {:keys [operand]}]
  (some-> (core/compile* context operand) to-time-op))
