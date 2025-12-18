(ns blaze.elm.compiler.clinical-operators
  "23. Clinical Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.elm.code :as code]
   [blaze.elm.code-system :as code-system]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.macros :refer [reify-expr]]
   [blaze.elm.concept :as concept]
   [blaze.elm.protocols :as p]
   [blaze.elm.value-set :as value-set]))

;; 23.3. CalculateAge
;;
;; see normalizer.clj

;; 23.4. CalculateAgeAt
(defn- calculate-age-at-op [birth-date date chrono-precision precision]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper-2 calculate-age-at-op cache birth-date
                                  date chrono-precision precision))
    (-resolve-refs [_ expression-defs]
      (core/resolve-refs-helper-2 calculate-age-at-op expression-defs birth-date
                                  date chrono-precision precision))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper-2 calculate-age-at-op parameters birth-date
                                    date chrono-precision precision))
    (-optimize [_ db]
      (core/optimize-helper-2 calculate-age-at-op db birth-date date
                              chrono-precision precision))
    (-eval [_ context resource scope]
      (p/duration-between
       (core/-eval birth-date context resource scope)
       (core/-eval date context resource scope)
       chrono-precision))
    (-form [_]
      (list 'calculate-age-at (core/-form birth-date) (core/-form date)
            precision))))

(defmethod core/compile* :elm.compiler.type/calculate-age-at
  [context {[birth-date date] :operand precision :precision}]
  (when-let [birth-date (core/compile* context birth-date)]
    (when-let [date (core/compile* context date)]
      (let [chrono-precision (some-> precision core/to-chrono-unit)]
        (calculate-age-at-op birth-date date chrono-precision precision)))))

;; 23.7. InCodeSystem
(defn- in-code-system [code code-system]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper in-code-system cache code code-system))
    (-resolve-refs [_ expression-defs]
      (core/resolve-refs-helper in-code-system expression-defs code code-system))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper in-code-system parameters code code-system))
    (-optimize [_ db]
      (core/optimize-helper in-code-system db code code-system))
    (-eval [_ context resource scope]
      (if-some [code (core/-eval code context resource scope)]
        (let [code-system (core/-eval code-system context resource scope)]
          (cond
            (string? code) (code-system/contains-string? code-system code)
            (code/code? code) (code-system/contains-code? code-system code)
            (concept/concept? code) (code-system/contains-concept? code-system code)))
        false))
    (-form [_]
      (list 'in-code-system (core/-form code) (core/-form code-system)))))

(defmethod core/compile* :elm.compiler.type/in-code-system
  [context
   {:keys [code] code-system :codesystem code-system-expression :codesystemExpression}]
  (let [code (core/compile* context code)]
    (if (nil? code)
      false
      (when-let [code-system (core/compile* context (or code-system code-system-expression))]
        (in-code-system code code-system)))))

;; 23.8. InValueSet
(defn- in-value-set [code value-set]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper in-value-set cache code value-set))
    (-resolve-refs [_ expression-defs]
      (core/resolve-refs-helper in-value-set expression-defs code value-set))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper in-value-set parameters code value-set))
    (-optimize [_ db]
      (core/optimize-helper in-value-set db code value-set))
    (-eval [_ context resource scope]
      (if-some [code (core/-eval code context resource scope)]
        (let [value-set (core/-eval value-set context resource scope)]
          (cond
            (string? code) (value-set/contains-string? value-set code)
            (code/code? code) (value-set/contains-code? value-set code)
            (concept/concept? code) (value-set/contains-concept? value-set code)))
        false))
    (-form [_]
      (list 'in-value-set (core/-form code) (core/-form value-set)))))

(defmethod core/compile* :elm.compiler.type/in-value-set
  [context
   {:keys [code] value-set :valueset value-set-expression :valuesetExpression}]
  (let [code (core/compile* context code)]
    (if (nil? code)
      false
      (when-let [value-set (core/compile* context (or value-set value-set-expression))]
        (in-value-set code value-set)))))
