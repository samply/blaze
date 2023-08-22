(ns blaze.elm.compiler.conditional-operators
  "15. Conditional Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.elm.compiler.core :as core]
    [blaze.elm.protocols :as p]))


;; 15.1. Case
(defmethod core/compile* :elm.compiler.type/case
  [context {:keys [comparand else] items :caseItem}]
  (let [comparand (some->> comparand (core/compile* context))
        items
        (map
          (fn [{:keys [when then]}]
            [(core/compile* context when)
             (core/compile* context then)])
          items)
        else (core/compile* context else)]
    (if comparand
      (reify core/Expression
        (-static [_]
          false)
        (-eval [_ context resource scope]
          (let [comparand (core/-eval comparand context resource scope)]
            (loop [[[when then] & next-items] items]
              (if (p/equal comparand (core/-eval when context resource scope))
                (core/-eval then context resource scope)
                (if (empty? next-items)
                  (core/-eval else context resource scope)
                  (recur next-items))))))
        (-form [_]
          `(~'case ~(core/-form comparand) ~@(map core/-form (flatten items)) ~else)))
      (reify core/Expression
        (-static [_]
          false)
        (-eval [_ context resource scope]
          (loop [[[when then] & next-items] items]
            (if (core/-eval when context resource scope)
              (core/-eval then context resource scope)
              (if (empty? next-items)
                (core/-eval else context resource scope)
                (recur next-items)))))
        (-form [_]
          `(~'case ~@(map core/-form (flatten items)) ~else))))))


;; 15.2. If
(defrecord IfExpression [condition then else]
  core/Expression
  (-static [_]
    false)
  (-eval [_ context resource scope]
    (if (core/-eval condition context resource scope)
      (core/-eval then context resource scope)
      (core/-eval else context resource scope))))


(defmethod core/compile* :elm.compiler.type/if
  [context {:keys [condition then else]}]
  (let [condition (core/compile* context condition)]
    (cond
      (true? condition) (core/compile* context then)
      (or (false? condition) (nil? condition)) (core/compile* context else)
      :else (->IfExpression condition (core/compile* context then)
                            (core/compile* context else)))))
