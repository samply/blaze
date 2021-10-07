(ns blaze.elm.compiler.list-operators
  "20. List Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.anomaly :as ba]
    [blaze.coll.core :as coll]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.macros :refer [defunop]]
    [blaze.elm.compiler.queries :as queries]
    [blaze.elm.protocols :as p]
    [cognitect.anomalies :as anom])
  (:import
    [clojure.lang ExceptionInfo]))


;; 20.1. List
(defrecord ListOperatorExpression [elements]
  core/Expression
  (-eval [_ context resource scope]
    (mapv #(core/-eval % context resource scope) elements)))


(defmethod core/compile* :elm.compiler.type/list
  [context {elements :element}]
  (let [elements (mapv #(core/compile* context %) elements)]
    (if (every? core/static? elements)
      elements
      (->ListOperatorExpression elements))))


;; 20.3. Current
(defmethod core/compile* :elm.compiler.type/current
  [_ {:keys [scope]}]
  (if scope
    (reify core/Expression
      (-eval [_ _ _ scopes]
        (get scopes scope)))
    (reify core/Expression
      (-eval [_ _ _ scope]
        scope))))


;; 20.4. Distinct
;;
;; TODO: implementation is O(n^2)
(defunop distinct [list]
  (when list
    (reduce
      (fn [result x]
        (if (p/contains result x nil)
          result
          (conj result x)))
      []
      list)))


;; 20.8. Exists
(defunop exists
  {:optimizations #{:first :non-distinct}}
  [list]
  (not (coll/empty? list)))


;; 20.9. Filter
(defmethod core/compile* :elm.compiler.type/filter
  [context {:keys [source condition scope]}]
  (let [source (core/compile* context source)
        condition (core/compile* context condition)]
    (if scope
      (reify core/Expression
        (-eval [_ context resource scopes]
          (when-let [source (core/-eval source context resource scopes)]
            (filterv
              (fn [x]
                (core/-eval condition context resource (assoc scopes scope x)))
              source))))
      (reify core/Expression
        (-eval [_ context resource scopes]
          (when-let [source (core/-eval source context resource scopes)]
            (filterv
              (fn [_]
                (core/-eval condition context resource scopes))
              source)))))))


;; 20.10. First
;;
;; TODO: orderBy
(defrecord FirstExpression [source]
  core/Expression
  (-eval [_ context resource scopes]
    (coll/first (core/-eval source context resource scopes))))


(defmethod core/compile* :elm.compiler.type/first
  [context {:keys [source]}]
  (let [source (core/compile* (assoc context :optimizations #{:first :non-distinct}) source)]
    (if (core/static? source)
      (first source)
      (->FirstExpression source))))


;; 20.11. Flatten
(defunop flatten [list]
  (when list
    (letfn [(flatten [to from]
              (reduce
                (fn [result x]
                  (if (sequential? x)
                    (flatten result x)
                    (conj result x)))
                to
                from))]
      (flatten [] list))))


;; 20.12. ForEach
(defmethod core/compile* :elm.compiler.type/for-each
  [context {:keys [source element scope]}]
  (let [source (core/compile* context source)
        element (core/compile* context element)]
    (if scope
      (reify core/Expression
        (-eval [_ context resource scopes]
          (when-let [source (core/-eval source context resource scopes)]
            (mapv
              (fn [x]
                (core/-eval element context resource (assoc scopes scope x)))
              source))))
      (reify core/Expression
        (-eval [_ context resource scopes]
          (when-let [source (core/-eval source context resource scopes)]
            (mapv
              (fn [_]
                (core/-eval element context resource scopes))
              source)))))))


;; 20.16. IndexOf
(defmethod core/compile* :elm.compiler.type/index-of
  [context {:keys [source element]}]
  (let [source (core/compile* context source)
        element (core/compile* context element)]
    (reify core/Expression
      (-eval [_ context resource scopes]
        (when-let [source (core/-eval source context resource scopes)]
          (when-let [element (core/-eval element context resource scopes)]
            (or
              (first
                (keep-indexed
                  (fn [idx x]
                    (when
                      (p/equal element x)
                      idx))
                  source))
              -1)))))))


;; 20.18. Last
;;
;; TODO: orderBy
(defmethod core/compile* :elm.compiler.type/last
  [context {:keys [source]}]
  (let [source (core/compile* context source)]
    (reify core/Expression
      (-eval [_ context resource scopes]
        (peek (core/-eval source context resource scopes))))))


;; 20.24. Repeat
;;
;; TODO: not implemented


;; 20.25. SingletonFrom
(defunop singleton-from [list expression]
  (try
    (p/singleton-from list)
    (catch ExceptionInfo e
      (if (::anom/category (ex-data e))
        (throw (ba/ex-anom (assoc (ex-data e) :expression expression)))
        (throw e)))))


;; 20.26. Slice
(defmethod core/compile* :elm.compiler.type/slice
  [context {:keys [source] start-index :startIndex end-index :endIndex}]
  (let [source (core/compile* context source)
        start-index (some->> start-index (core/compile* context))
        end-index (some->> end-index (core/compile* context))]
    (reify core/Expression
      (-eval [_ context resource scopes]
        (when-let [source (core/-eval source context resource scopes)]
          (let [start-index (or (core/-eval start-index context resource scopes) 0)
                end-index (or (core/-eval end-index context resource scopes) (count source))]
            (if (or (neg? start-index) (< end-index start-index))
              []
              (subvec source start-index end-index))))))))


(defrecord SortByDirectionExpression [source comp]
  core/Expression
  (-eval [_ context resource scopes]
    (when-let [source (core/-eval source context resource scopes)]
      (sort-by identity comp source))))


;; 20.27. Sort
(defmethod core/compile* :elm.compiler.type/sort
  [context {:keys [source] sort-by-items :by}]
  (let [source (core/compile* context source)
        xform #(queries/compile-sort-by-item context %)
        sort-by-items (mapv xform sort-by-items)]
    (reduce
      (fn [source {:keys [type direction]}]
        (case type
          "ByDirection"
          (->SortByDirectionExpression source (queries/comparator direction))))
      source
      sort-by-items)))
