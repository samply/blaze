(ns blaze.elm.compiler.nullological-operators
  "14. Nullological Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.macros :refer [defunop reify-expr]]))

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
(defn- coalesce-op [operands]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper-list coalesce-op cache operands))
    (-resolve-refs [_ expression-defs]
      (coalesce-op (mapv #(core/-resolve-refs % expression-defs) operands)))
    (-resolve-params [_ parameters]
      (coalesce-op (mapv #(core/-resolve-params % parameters) operands)))
    (-optimize [_ node]
      (coalesce-op (mapv #(core/-optimize % node) operands)))
    (-eval [_ context resource scope]
      (reduce
       (fn [_ operand]
         (let [operand (core/-eval operand context resource scope)]
           (when (some? operand)
             (reduced operand))))
       nil
       operands))
    (-form [_]
      `(~'coalesce ~@(map core/-form operands)))))

(defn coalesce-list-op [list]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper coalesce-op cache list))
    (-resolve-refs [_ expression-defs]
      (coalesce-op (core/-resolve-refs list expression-defs)))
    (-resolve-params [_ parameters]
      (coalesce-op (core/-resolve-params list parameters)))
    (-eval [_ context resource scope]
      (reduce
       (fn [_ elem]
         (let [elem (core/-eval elem context resource scope)]
           (when (some? elem)
             (reduced elem))))
       nil
       (core/-eval list context resource scope)))))

(defmethod core/compile* :elm.compiler.type/coalesce
  [context {operands :operand}]
  (if (= 1 (count operands))
    (let [operand (first operands)]
      (cond-> (core/compile* context operand)
        (= "List" (:type operand))
        coalesce-list-op))
    (coalesce-op (mapv #(core/compile* context %) operands))))

;; 14.3. IsFalse
(defunop is-false [operand]
  (false? operand))

;; 14.4. IsNull
(defunop is-null [operand]
  (nil? operand))

;; 14.5. IsTrue
(defunop is-true [operand]
  (true? operand))
