(ns life-fhir-store.elm.normalizer
  (:require
    [camel-snake-kebab.core :refer [->kebab-case-string]]
    [clojure.spec.alpha :as s]
    [life-fhir-store.elm.spec]))


(defmulti normalize
  {:arglists '([expression])}
  (fn [{:keys [type]}]
    (assert type)
    (keyword "elm.normalizer.type" (->kebab-case-string type))))


(defn- update-expression-defs [expression-defs]
  (mapv #(update % :expression normalize) expression-defs))


(s/fdef normalize-library
  :args (s/cat :library :elm/library))

(defn normalize-library [library]
  (update-in library [:statements :def] update-expression-defs))


(defmethod normalize :default
  [expression]
  expression)



;; 2. Structured Values

;; 2.3. Property
(defmethod normalize :elm.normalizer.type/property
  [{:keys [source] :as expression}]
  (let [source (some-> source normalize)]
    (cond-> expression
      source
      (assoc :source source))))



;; 8. Expressions

;; 8.3. UnaryExpression
(defmethod normalize :elm.normalizer.type/unary-expression
  [{:keys [operand] :as expression}]
  (assoc expression :operand (normalize operand)))


;; 8.4. BinaryExpression
;; 8.5. TernaryExpression
;; 8.6. NaryExpression
(defmethod normalize :elm.normalizer.type/multiary-expression
  [{:keys [operand] :as expression}]
  (assoc expression :operand (mapv normalize operand)))



;; 10. Queries

;; 10.1. Query
(defmethod normalize :elm.normalizer.type/query
  [{:keys [source let relationship where return] :as expression}]
  (cond-> (assoc expression
            :source (mapv #(update % :expression normalize) source))
    let
    (assoc :let (mapv #(update % :expression normalize) let))

    relationship
    (assoc :relationship (mapv #(update % :expression normalize) relationship))

    where
    (assoc :where (normalize where))

    return
    (assoc :return (update return :expression normalize))))



;; 12. Comparison Operators

;; 12.1. Equal
(derive :elm.normalizer.type/equal :elm.normalizer.type/multiary-expression)


;; 12.2. Equivalent
(derive :elm.normalizer.type/equivalent :elm.normalizer.type/multiary-expression)


;; 12.3. Greater
(derive :elm.normalizer.type/greater :elm.normalizer.type/multiary-expression)


;; 12.4. GreaterOrEqual
(derive :elm.normalizer.type/greater-or-equal :elm.normalizer.type/multiary-expression)


;; 12.5. Less
(derive :elm.normalizer.type/less :elm.normalizer.type/multiary-expression)


;; 12.6. LessOrEqual
(derive :elm.normalizer.type/less-or-equal :elm.normalizer.type/multiary-expression)


;; 12.7. NotEqual
(derive :elm.normalizer.type/not-equal :elm.normalizer.type/multiary-expression)



;; 13. Logical Operators

;; 13.1 And
(derive :elm.normalizer.type/and :elm.normalizer.type/multiary-expression)


;; 13.2 Implies
(defmethod normalize :elm.normalizer.type/implies
  [{[operand-1 operand-2] :operand}]
  {:type "Or" :operand [{:type "Not" :operand operand-1} operand-2]})


;; 13.3. Not
(derive :elm.normalizer.type/not :elm.normalizer.type/unary-expression)


;; 13.4 Or
(derive :elm.normalizer.type/or :elm.normalizer.type/multiary-expression)


;; 13.5 Xor
(defmethod normalize :elm.normalizer.type/xor
  [{[operand-1 operand-2] :operand}]
  {:type "Or"
   :operand [{:type "And"
              :operand [{:type "Not" :operand operand-1} operand-2]}
             {:type "And"
              :operand [operand-1 {:type "Not" :operand operand-2}]}]})



;; 14. Nullological Operators

;; 14.2. Coalesce
(derive :elm.normalizer.type/coalesce :elm.normalizer.type/multiary-expression)


;; 14.3. IsFalse
(derive :elm.normalizer.type/is-false :elm.normalizer.type/unary-expression)


;; 14.4. IsNull
(derive :elm.normalizer.type/is-null :elm.normalizer.type/unary-expression)


;; 14.3. IsTrue
(derive :elm.normalizer.type/is-true :elm.normalizer.type/unary-expression)
