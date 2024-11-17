(ns blaze.elm.compiler.conditional-operators
  "15. Conditional Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.macros :refer [reify-expr]]
   [blaze.elm.protocols :as p]))

;; 15.1. Case
(defn- attach-cache [items cache]
  (reduce
   (fn [[items bfs] [when then]]
     (let [[when w-bfs] ((first (core/-attach-cache when cache)))
           [then t-bfs] ((first (core/-attach-cache then cache)))]
       [(conj items [when then]) (into bfs (into w-bfs t-bfs))]))
   [[] []]
   items))

(defn- resolve-refs [items expression-defs]
  (mapv
   (fn [[when then]]
     [(core/-resolve-refs when expression-defs)
      (core/-resolve-refs then expression-defs)])
   items))

(defn- resolve-params [items parameters]
  (mapv
   (fn [[when then]]
     [(core/-resolve-params when parameters)
      (core/-resolve-params then parameters)])
   items))

(defn- optimize [items db]
  (mapv
   (fn [[when then]]
     [(core/-optimize when db)
      (core/-optimize then db)])
   items))

(defn- comparand-case-op [comparand items else]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (let [[comparand c-bfs] ((first (core/-attach-cache comparand cache)))
            [items i-bfs] (attach-cache items cache)
            [else e-bfs] ((first (core/-attach-cache else cache)))]
        [(fn [] [(comparand-case-op comparand items else) (into c-bfs (into i-bfs e-bfs))])]))
    (-resolve-refs [_ expression-defs]
      (comparand-case-op
       (core/-resolve-refs comparand expression-defs)
       (resolve-refs items expression-defs)
       (core/-resolve-refs else expression-defs)))
    (-resolve-params [_ parameters]
      (comparand-case-op
       (core/-resolve-params comparand parameters)
       (resolve-params items parameters)
       (core/-resolve-params else parameters)))
    (-optimize [_ db]
      (comparand-case-op
       (core/-optimize comparand db)
       (optimize items db)
       (core/-optimize else db)))
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
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (let [[items i-bfs] (attach-cache items cache)
            [else e-bfs] ((first (core/-attach-cache else cache)))]
        [(fn [] [(multi-conditional-case-op items else) (into i-bfs e-bfs)])]))
    (-resolve-refs [_ expression-defs]
      (multi-conditional-case-op
       (resolve-refs items expression-defs)
       (core/-resolve-refs else expression-defs)))
    (-resolve-params [_ parameters]
      (multi-conditional-case-op
       (resolve-params items parameters)
       (core/-resolve-params else parameters)))
    (-optimize [_ db]
      (multi-conditional-case-op
       (optimize items db)
       (core/-optimize else db)))
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
        (mapv
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
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper if-op cache condition then else))
    (-resolve-refs [_ expression-defs]
      (core/resolve-refs-helper if-op expression-defs condition then else))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper if-op parameters condition then else))
    (-optimize [_ db]
      (core/optimize-helper if-op db condition then else))
    (-eval [_ context resource scope]
      (if (core/-eval condition context resource scope)
        (core/-eval then context resource scope)
        (core/-eval else context resource scope)))
    (-form [_]
      (list 'if (core/-form condition) (core/-form then) (core/-form else)))))

(defmethod core/compile* :elm.compiler.type/if
  [context {:keys [condition then else]}]
  (let [condition (core/compile* context condition)]
    (cond
      (true? condition) (core/compile* context then)
      (or (false? condition) (nil? condition)) (core/compile* context else)
      :else (if-op condition (core/compile* context then)
                   (core/compile* context else)))))
