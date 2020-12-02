(ns blaze.elm.compiler.conditional-operators
  "15. Conditional Operators"
  (:require
    [blaze.elm.compiler.core :as core]
    [blaze.elm.protocols :as p]))


;; 15.1. Case
(defrecord ComparandCaseExpression [comparand items else]
  core/Expression
  (-eval [_ context resource scope]
    (let [comparand (core/-eval comparand context resource scope)]
      (loop [[{:keys [when then]} & next-items] items]
        (if (p/equal comparand (core/-eval when context resource scope))
          (core/-eval then context resource scope)
          (if (empty? next-items)
            (core/-eval else context resource scope)
            (recur next-items)))))))


(defrecord MultiConditionalCaseExpression [items else]
  core/Expression
  (-eval [_ context resource scope]
    (loop [[{:keys [when then]} & next-items] items]
      (if (core/-eval when context resource scope)
        (core/-eval then context resource scope)
        (if (empty? next-items)
          (core/-eval else context resource scope)
          (recur next-items))))))


(defmethod core/compile* :elm.compiler.type/case
  [context {:keys [comparand else] items :caseItem}]
  (let [comparand (some->> comparand (core/compile* context))
        items
        (mapv
          (fn [{:keys [when then]}]
            {:when (core/compile* context when)
             :then (core/compile* context then)})
          items)
        else (core/compile* context else)]
    (if comparand
      (->ComparandCaseExpression comparand items else)
      (->MultiConditionalCaseExpression items else))))


;; 15.2. If
(defrecord IfExpression [condition then else]
  core/Expression
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
