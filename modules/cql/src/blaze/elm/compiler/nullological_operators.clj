(ns blaze.elm.compiler.nullological-operators
  "14. Nullological Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.macros :refer [defunop]]))

;; 14.1. Null
(defmethod core/compile* :elm.compiler.type/null
  [_ _])

;; 14.2. Coalesce
;;
;; The Coalesce operator returns the first non-null result in a list of
;; arguments. If all arguments evaluate to null, the result is null. The static
;; type of the first argument determines the type of the result, and all
;; subsequent arguments must be of that same type.
;;
;; TODO: The list type argument is missing in the doc.
(defmethod core/compile* :elm.compiler.type/coalesce
  [context {operands :operand}]
  (if (= 1 (count operands))
    (let [operand (first operands)]
      (if (= "List" (:type operand))
        (let [operand (core/compile* context operand)]
          (reify core/Expression
            (-static [_]
              false)
            (-eval [_ context resource scope]
              (reduce
               (fn [_ elem]
                 (let [elem (core/-eval elem context resource scope)]
                   (when (some? elem)
                     (reduced elem))))
               nil
               (core/-eval operand context resource scope)))))
        (let [operand (core/compile* context operand)]
          (reify core/Expression
            (-static [_]
              false)
            (-eval [_ context resource scope]
              (core/-eval operand context resource scope))))))
    (let [operands (mapv #(core/compile* context %) operands)]
      (reify core/Expression
        (-static [_]
          false)
        (-eval [_ context resource scope]
          (reduce
           (fn [_ operand]
             (let [operand (core/-eval operand context resource scope)]
               (when (some? operand)
                 (reduced operand))))
           nil
           operands))))))

;; 14.3. IsFalse
(defunop is-false [operand]
  (false? operand))

;; 14.4. IsNull
(defunop is-null [operand]
  (nil? operand))

;; 14.5. IsTrue
(defunop is-true [operand]
  (true? operand))
