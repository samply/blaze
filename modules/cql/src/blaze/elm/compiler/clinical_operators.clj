(ns blaze.elm.compiler.clinical-operators
  "23. Clinical Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.elm.compiler.core :as core]
    [blaze.elm.protocols :as p]))


;; 23.3. CalculateAge
;;
;; see normalizer.clj


;; 23.4. CalculateAgeAt
(defn- calculate-age-at-op [birth-date date chrono-precision precision]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (calculate-age-at-op (core/-attach-cache birth-date cache)
                           (core/-attach-cache date cache)
                           chrono-precision precision))
    (-resolve-refs [_ expression-defs]
      (calculate-age-at-op (core/-resolve-refs birth-date expression-defs)
                           (core/-resolve-refs date expression-defs)
                           chrono-precision precision))
    (-resolve-params [_ parameters]
      (calculate-age-at-op (core/-resolve-params birth-date parameters)
                           (core/-resolve-params date parameters)
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
