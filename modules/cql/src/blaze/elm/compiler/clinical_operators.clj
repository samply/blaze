(ns blaze.elm.compiler.clinical-operators
  "23. Clinical Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.macros :refer [reify-expr]]
   [blaze.elm.protocols :as p]))

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
    (-optimize [_ node]
      (core/optimize-helper-2 calculate-age-at-op node birth-date date
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
