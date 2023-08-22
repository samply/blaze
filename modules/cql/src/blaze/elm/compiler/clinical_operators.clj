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
(defmethod core/compile* :elm.compiler.type/calculate-age-at
  [context {[birth-date date] :operand precision :precision}]
  (when-let [birth-date (core/compile* context birth-date)]
    (when-let [date (core/compile* context date)]
      (let [chrono-precision (some-> precision core/to-chrono-unit)]
        (reify core/Expression
          (-static [_]
            false)
          (-eval [_ context resource scope]
            (p/duration-between
              (core/-eval birth-date context resource scope)
              (core/-eval date context resource scope)
              chrono-precision))
          (-form [_]
            (list 'calculate-age-at (core/-form birth-date) (core/-form date)
                  precision)))))))
