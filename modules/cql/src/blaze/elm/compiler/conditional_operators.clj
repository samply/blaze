(ns blaze.elm.compiler.conditional-operators
  "15. Conditional Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.elm.compiler.core :as core]
    [blaze.elm.protocols :as p]))


;; 15.1. Case
(defn- attach-cache [items cache]
  (map
    (fn [[when then]]
      [(core/-attach-cache when cache)
       (core/-attach-cache then cache)])
    items))

(defn- resolve-refs [items expression-defs]
  (map
    (fn [[when then]]
      [(core/-resolve-refs when expression-defs)
       (core/-resolve-refs then expression-defs)])
    items))

(defn- resolve-param-refs [items parameters]
  (map
    (fn [[when then]]
      [(core/-resolve-params when parameters)
       (core/-resolve-params then parameters)])
    items))

(defn- comparand-case-op [comparand items else]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (comparand-case-op
        (core/-attach-cache comparand cache)
        (attach-cache items cache)
        (core/-attach-cache else cache)))
    (-resolve-refs [_ expression-defs]
      (comparand-case-op
        (core/-resolve-refs comparand expression-defs)
        (resolve-refs items expression-defs)
        (core/-resolve-refs else expression-defs)))
    (-resolve-params [_ parameters]
      (comparand-case-op
        (core/-resolve-params comparand parameters)
        (resolve-param-refs items parameters)
        (core/-resolve-params else parameters)))
    (-eval [_ context resource scope]
      (let [comparand (core/-eval comparand context resource scope)]
        (loop [[[when then] & next-items] items]
          (if (p/equal comparand (core/-eval when context resource scope))
            (core/-eval then context resource scope)
            (if (empty? next-items)
              (core/-eval else context resource scope)
              (recur next-items))))))
    (-form [_]
      `(~'case ~(core/-form comparand) ~@(map core/-form (flatten items)) ~(core/-form else)))))

(defn- multi-conditional-case-op [items else]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (multi-conditional-case-op
        (attach-cache items cache)
        (core/-attach-cache else cache)))
    (-resolve-refs [_ expression-defs]
      (multi-conditional-case-op
        (resolve-refs items expression-defs)
        (core/-resolve-refs else expression-defs)))
    (-resolve-params [_ parameters]
      (multi-conditional-case-op
        (resolve-param-refs items parameters)
        (core/-resolve-params else parameters)))
    (-eval [_ context resource scope]
      (loop [[[when then] & next-items] items]
        (if (core/-eval when context resource scope)
          (core/-eval then context resource scope)
          (if (empty? next-items)
            (core/-eval else context resource scope)
            (recur next-items)))))
    (-form [_]
      `(~'case ~@(map core/-form (flatten items)) ~(core/-form else)))))

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
      (comparand-case-op comparand items else)
      (multi-conditional-case-op items else))))


;; 15.2. If
(defn- if-op [condition then else]
  (reify
    core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (if-op
        (core/-attach-cache condition cache)
        (core/-attach-cache then cache)
        (core/-attach-cache else cache)))
    (-resolve-refs [_ expression-defs]
      (if-op
        (core/-resolve-refs condition expression-defs)
        (core/-resolve-refs then expression-defs)
        (core/-resolve-refs else expression-defs)))
    (-resolve-params [_ parameters]
      (if-op
        (core/-resolve-params condition parameters)
        (core/-resolve-params then parameters)
        (core/-resolve-params else parameters)))
    (-eval [_ context resource scope]
      (if (core/-eval condition context resource scope)
        (core/-eval then context resource scope)
        (core/-eval else context resource scope)))
    (-form [_]
      (list 'if (core/-form condition) (core/-form then)
            (core/-form else)))))


(defmethod core/compile* :elm.compiler.type/if
  [context {:keys [condition then else]}]
  (let [condition (core/compile* context condition)]
    (cond
      (true? condition) (core/compile* context then)
      (or (false? condition) (nil? condition)) (core/compile* context else)
      :else (if-op condition (core/compile* context then)
                   (core/compile* context else)))))
