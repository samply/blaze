(ns blaze.elm.normalizer
  (:require
    [blaze.elm.spec]
    [clojure.spec.alpha :as s]
    [cuerdas.core :as str]))


(defmulti normalize
  {:arglists '([expression])}
  (fn [{:keys [type]}]
    (assert type)
    (keyword "elm.normalizer.type" (str/kebab type))))


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
  [{:keys [source relationship where return] let' :let :as expression}]
  (cond-> (assoc expression
            :source (mapv #(update % :expression normalize) source))
    let'
    (assoc :let (mapv #(update % :expression normalize) let'))

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
(defmethod normalize :elm.normalizer.type/not-equal
  [{[operand-1 operand-2] :operand}]
  (let [operand-1 (normalize operand-1)
        operand-2 (normalize operand-2)]
    {:type "Not"
     :operand
     {:type "Equal"
      :operand [operand-1 operand-2]
      :resultTypeName "{urn:hl7-org:elm-types:r1}Boolean"}
     :resultTypeName "{urn:hl7-org:elm-types:r1}Boolean"}))


;; 13. Logical Operators

;; 13.1 And
(derive :elm.normalizer.type/and :elm.normalizer.type/multiary-expression)


;; 13.2 Implies
(defmethod normalize :elm.normalizer.type/implies
  [{[operand-1 operand-2] :operand}]
  (let [operand-1 (normalize operand-1)
        operand-2 (normalize operand-2)]
    {:type "Or"
     :operand
     [{:type "Not"
       :operand operand-1
       :resultTypeName "{urn:hl7-org:elm-types:r1}Boolean"}
      operand-2]
     :resultTypeName "{urn:hl7-org:elm-types:r1}Boolean"}))


;; 13.3. Not
(derive :elm.normalizer.type/not :elm.normalizer.type/unary-expression)


;; 13.4 Or
(derive :elm.normalizer.type/or :elm.normalizer.type/multiary-expression)


;; 13.5 Xor
(derive :elm.normalizer.type/xor :elm.normalizer.type/multiary-expression)



;; 14. Nullological Operators

;; 14.2. Coalesce
(derive :elm.normalizer.type/coalesce :elm.normalizer.type/multiary-expression)


;; 14.3. IsFalse
(derive :elm.normalizer.type/is-false :elm.normalizer.type/unary-expression)


;; 14.4. IsNull
(derive :elm.normalizer.type/is-null :elm.normalizer.type/unary-expression)


;; 14.3. IsTrue
(derive :elm.normalizer.type/is-true :elm.normalizer.type/unary-expression)



;; 19. Interval Operators

;; 19.12. In
(defmethod normalize :elm.normalizer.type/in
  [{[operand-1 operand-2] :operand :keys [precision]}]
  (let [operand-1 (normalize operand-1)
        operand-2 (normalize operand-2)]
    (cond->
      {:type "Contains"
       :operand [operand-2 operand-1]
       :resultTypeName "{urn:hl7-org:elm-types:r1}Boolean"}
      precision
      (assoc :precision precision))))


;; 19.14. IncludedIn
(defmethod normalize :elm.normalizer.type/included-in
  [{[operand-1 operand-2] :operand :keys [precision]}]
  (let [operand-1 (normalize operand-1)
        operand-2 (normalize operand-2)]
    (cond->
      {:type "Includes"
       :operand [operand-2 operand-1]
       :resultTypeName "{urn:hl7-org:elm-types:r1}Boolean"}
      precision
      (assoc :precision precision))))


;; 19.16. Meets
(defmethod normalize :elm.normalizer.type/meets
  [{[operand-1 operand-2] :operand :keys [precision]}]
  (let [operand-1 (normalize operand-1)
        operand-2 (normalize operand-2)]
    {:type "Or"
     :operand
     [(cond->
        {:type "MeetsBefore"
         :operand [operand-1 operand-2]
         :resultTypeName "{urn:hl7-org:elm-types:r1}Boolean"}
        precision
        (assoc :precision precision))
      (cond->
        {:type "MeetsAfter"
         :operand [operand-1 operand-2]
         :resultTypeName "{urn:hl7-org:elm-types:r1}Boolean"}
        precision
        (assoc :precision precision))]
     :resultTypeName "{urn:hl7-org:elm-types:r1}Boolean"}))


;; 19.20. Overlaps
(defmethod normalize :elm.normalizer.type/overlaps
  [{[operand-1 operand-2] :operand :keys [precision]}]
  (let [operand-1 (normalize operand-1)
        operand-2 (normalize operand-2)]
    {:type "Or"
     :operand
     [(normalize
        (cond->
          {:type "OverlapsBefore"
           :operand [operand-1 operand-2]
           :resultTypeName "{urn:hl7-org:elm-types:r1}Boolean"}
          precision
          (assoc :precision precision)))
      (normalize
        (cond->
          {:type "OverlapsAfter"
           :operand [operand-1 operand-2]
           :resultTypeName "{urn:hl7-org:elm-types:r1}Boolean"}
          precision
          (assoc :precision precision)))]
     :resultTypeName "{urn:hl7-org:elm-types:r1}Boolean"}))


;; 19.21. OverlapsBefore
(defmethod normalize :elm.normalizer.type/overlaps-before
  [{[operand-1 operand-2] :operand :keys [precision]}]
  (let [operand-1 (normalize operand-1)
        operand-2 (normalize operand-2)]
    (cond->
      {:type "ProperContains"
       :operand
       [operand-1
        (cond->
          {:type "Start"
           :operand operand-2}
          (:resultTypeName operand-2)
          (assoc :resultTypeName (:resultTypeName operand-2)))]
       :resultTypeName "{urn:hl7-org:elm-types:r1}Boolean"}
      precision
      (assoc :precision precision))))


;; 19.22. OverlapsAfter
(defmethod normalize :elm.normalizer.type/overlaps-after
  [{[operand-1 operand-2] :operand :keys [precision]}]
  (let [operand-1 (normalize operand-1)
        operand-2 (normalize operand-2)]
    (cond->
      {:type "ProperContains"
       :operand
       [operand-1
        (cond->
          {:type "End"
           :operand operand-2}
          (:resultTypeName operand-2)
          (assoc :resultTypeName (:resultTypeName operand-2)))]
       :resultTypeName "{urn:hl7-org:elm-types:r1}Boolean"}
      precision
      (assoc :precision precision))))


;; 19.25. ProperIn
(defmethod normalize :elm.normalizer.type/proper-in
  [{[operand-1 operand-2] :operand :keys [precision]}]
  (let [operand-1 (normalize operand-1)
        operand-2 (normalize operand-2)]
    (cond->
      {:type "ProperContains"
       :operand [operand-2 operand-1]
       :resultTypeName "{urn:hl7-org:elm-types:r1}Boolean"}
      precision
      (assoc :precision precision))))


;; 19.27. ProperIncludedIn
(defmethod normalize :elm.normalizer.type/proper-included-in
  [{[operand-1 operand-2] :operand :keys [precision]}]
  (let [operand-1 (normalize operand-1)
        operand-2 (normalize operand-2)]
    (cond->
      {:type "ProperIncludes"
       :operand [operand-2 operand-1]
       :resultTypeName "{urn:hl7-org:elm-types:r1}Boolean"}
      precision
      (assoc :precision precision))))



;; 23. Clinical Operators

;; 23.3. CalculateAge
(defmethod normalize :elm.normalizer.type/calculate-age
  [{birth-date :operand :keys [precision]}]
  (let [birth-date (normalize birth-date)]
    (cond->
      {:type "CalculateAgeAt"
       :operand [birth-date {:type "Today"}]
       :resultTypeName "{urn:hl7-org:elm-types:r1}Integer"}
      precision
      (assoc :precision precision))))
