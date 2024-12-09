(ns blaze.elm.compiler.clinical-operators
  "23. Clinical Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.elm.compiler.clinical-operators.impl :as impl]
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
      (let [value-set (core/-eval value-set context resource scope)
            code (core/-eval code context resource scope)]
        (cond
          (string? code) (contains? (into #{} (map :code) value-set) code)
          (concept/concept? code) (impl/contains-any? value-set (:codes code))
          :else (contains? value-set (value-set/from-code code)))))
    (-form [_]
      (list 'in-value-set (core/-form code) (core/-form value-set)))))

(defmethod core/compile* :elm.compiler.type/in-value-set
  [context {:keys [code] value-set :valueset}]
  (let [code (core/compile* context code)]
    (when-let [value-set (core/compile* context value-set)]
      (in-value-set code value-set))))
