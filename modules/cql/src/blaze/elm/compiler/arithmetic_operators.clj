(ns blaze.elm.compiler.arithmetic-operators
  "16. Arithmetic Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.anomaly :as ba :refer [throw-anom]]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.macros :refer [defbinop defunop]]
    [blaze.elm.date-time :as date-time]
    [blaze.elm.decimal :as decimal]
    [blaze.elm.protocols :as p]
    [blaze.elm.util :as elm-util]))


;; 16.1. Abs
(defunop abs [x]
  (p/abs x))


;; 16.2. Add
(defbinop add [x y]
  (p/add x y))


;; 16.3. Ceiling
(defunop ceiling [x]
  (p/ceiling x))


;; 16.4. Divide
(defbinop divide [x y]
  (p/divide x y))


;; 16.5. Exp
(defunop exp [x]
  (p/exp x))


;; 16.6. Floor
(defunop floor [x]
  (p/floor x))


;; 16.8. Log
(defbinop log [x base]
  (p/log x base))


;; 16.10. Ln
(defunop ln [x]
  (p/ln x))


;; 16.11. MaxValue
(defn- incorrect-type-msg-anom [name type]
  (ba/incorrect (format "Incorrect type `%s`." name) :type type))


(defn- incorrect-type-ns-msg-anom [ns type]
  (ba/incorrect (format "Incorrect type namespace `%s`." ns) :type type))


(defn max-value [type]
  (let [[ns name] (elm-util/parse-qualified-name type)]
    (case ns
      "urn:hl7-org:elm-types:r1"
      (case name
        "Integer" (long Integer/MAX_VALUE)
        "Long" Long/MAX_VALUE
        "Decimal" decimal/max
        "Date" date-time/max-date
        "DateTime" date-time/max-date-time
        "Time" date-time/max-time
        (throw-anom (incorrect-type-msg-anom name type)))
      (throw-anom (incorrect-type-ns-msg-anom ns type)))))


(defmethod core/compile* :elm.compiler.type/max-value
  [_ {type :valueType}]
  (max-value type))


;; 16.12. MinValue
(defn min-value [type]
  (let [[ns name] (elm-util/parse-qualified-name type)]
    (case ns
      "urn:hl7-org:elm-types:r1"
      (case name
        "Integer" (long Integer/MIN_VALUE)
        "Long" Long/MIN_VALUE
        "Decimal" decimal/min
        "Date" date-time/min-date
        "DateTime" date-time/min-date-time
        "Time" date-time/min-time
        (throw-anom (incorrect-type-msg-anom name type)))
      (throw-anom (incorrect-type-ns-msg-anom ns type)))))


(defmethod core/compile* :elm.compiler.type/min-value
  [_ {type :valueType}]
  (min-value type))


;; 16.13. Modulo
(defbinop modulo [num div]
  (p/modulo num div))


;; 16.14. Multiply
(defbinop multiply [x y]
  (p/multiply x y))


;; 16.15. Negate
(defunop negate [x]
  (p/negate x))


;; 16.16. Power
(defbinop power [x exp]
  (p/power x exp))


;; 16.18. Predecessor
(defunop predecessor [x]
  (p/predecessor x))


;; 16.19. Round
(defmethod core/compile* :elm.compiler.type/round
  [context {:keys [operand precision]}]
  (let [operand (core/compile* context operand)
        precision (some->> precision (core/compile* context))]
    (if (and (core/static? operand) (core/static? precision))
      (p/round operand precision)
      (reify core/Expression
        (-eval [_ context resource scope]
          (p/round (core/-eval operand context resource scope)
                   (core/-eval precision context resource scope)))
        (-form [_]
          (->> (some-> (core/-form precision) list)
               (cons (core/-form operand))
               (cons 'round)))))))


;; 16.20. Subtract
(defbinop subtract [x y]
  (p/subtract x y))


;; 16.21. Successor
(defunop successor [x]
  (p/successor x))


;; 16.22. Truncate
(defunop truncate [x]
  (p/truncate x))


;; 16.23. TruncatedDivide
(defbinop truncated-divide [num div]
  (p/truncated-divide num div))
