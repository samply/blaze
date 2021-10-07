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
(defrecord CalculateAgeAtExpression [birth-date date precision]
  core/Expression
  (-eval [_ {:keys [now] :as context} resource scope]
    (p/duration-between
      (p/to-date (core/-eval birth-date context resource scope) now)
      (core/-eval date context resource scope)
      precision)))


(defmethod core/compile* :elm.compiler.type/calculate-age-at
  [context {[birth-date date] :operand precision :precision}]
  (when-let [birth-date (core/compile* context birth-date)]
    (when-let [date (core/compile* context date)]
      (->CalculateAgeAtExpression
        birth-date
        date
        (some-> precision core/to-chrono-unit)))))
