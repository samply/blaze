(ns blaze.elm.compiler.list-operators
  "20. List Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.anomaly :as ba]
   [blaze.coll.core :as coll]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.macros :refer [defbinop defunop]]
   [blaze.elm.compiler.queries :as queries]
   [blaze.elm.protocols :as p]
   [cognitect.anomalies :as anom])
  (:import
   [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

;; 20.1. List
(defn list-op [elements]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (list-op (mapv #(core/-attach-cache % cache) elements)))
    (-resolve-refs [_ expression-defs]
      (list-op (mapv #(core/-resolve-refs % expression-defs) elements)))
    (-resolve-params [_ parameters]
      (list-op (mapv #(core/-resolve-params % parameters) elements)))
    (-eval [_ context resource scope]
      (mapv #(core/-eval % context resource scope) elements))
    (-form [_]
      `(~'list ~@(map core/-form elements)))))

(defmethod core/compile* :elm.compiler.type/list
  [context {elements :element}]
  (let [elements (mapv #(core/compile* context %) elements)]
    (cond-> elements (some (comp not core/static?) elements) list-op)))

;; 20.3. Current
(defmethod core/compile* :elm.compiler.type/current
  [_ {:keys [scope]}]
  (if scope
    (reify core/Expression
      (-static [_]
        false)
      (-attach-cache [expr _]
        expr)
      (-resolve-refs [expr _]
        expr)
      (-resolve-params [expr _]
        expr)
      (-eval [_ _ _ scopes]
        (get scopes scope))
      (-form [_]
        (list 'current scope)))
    (reify core/Expression
      (-static [_]
        false)
      (-attach-cache [expr _]
        expr)
      (-resolve-refs [expr _]
        expr)
      (-resolve-params [expr _]
        expr)
      (-eval [_ _ _ scope]
        scope)
      (-form [_]
        'current))))

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
  {:optimizations #{:first :non-distinct}
   :cache true}
  [list]
  (not (coll/empty? list)))

;; 20.9. Filter
(defmethod core/compile* :elm.compiler.type/filter
  [context {:keys [source condition scope]}]
  (let [source (core/compile* context source)
        condition (core/compile* context condition)]
    (if scope
      (reify core/Expression
        (-static [_]
          false)
        (-eval [_ context resource scopes]
          (when-let [source (core/-eval source context resource scopes)]
            (filterv
             (fn [x]
               (core/-eval condition context resource (assoc scopes scope x)))
             source)))
        (-form [_]
          (list 'filter (core/-form source) (core/-form condition) scope)))
      (reify core/Expression
        (-static [_]
          false)
        (-eval [_ context resource scopes]
          (when-let [source (core/-eval source context resource scopes)]
            (filterv (partial core/-eval condition context resource) source)))
        (-form [_]
          (list 'filter (core/-form source) (core/-form condition)))))))

;; 20.10. First
;;
;; TODO: orderBy
(defn first-op [source]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (first-op (core/-attach-cache source cache)))
    (-resolve-refs [_ expression-defs]
      (first-op (core/-resolve-refs source expression-defs)))
    (-resolve-params [_ parameters]
      (first-op (core/-resolve-params source parameters)))
    (-eval [_ context resource scopes]
      (coll/first (core/-eval source context resource scopes)))
    (-form [_]
      (list 'first (core/-form source)))))

(defmethod core/compile* :elm.compiler.type/first
  [context {:keys [source]}]
  (let [source (core/compile* (assoc context :optimizations #{:first :non-distinct}) source)]
    (if (core/static? source)
      (first source)
      (first-op source))))

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
        (-static [_]
          false)
        (-eval [_ context resource scopes]
          (when-let [source (core/-eval source context resource scopes)]
            (mapv
             (fn [x]
               (core/-eval element context resource (assoc scopes scope x)))
             source)))
        (-form [_]
          (list 'for-each (core/-form source) (core/-form element) scope)))
      (reify core/Expression
        (-static [_]
          false)
        (-eval [_ context resource scopes]
          (when-let [source (core/-eval source context resource scopes)]
            (mapv (partial core/-eval element context resource) source)))
        (-form [_]
          (list 'for-each (core/-form source) (core/-form element)))))))

;; 20.16. IndexOf
(defn- index-of-op [source element]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (index-of-op (core/-attach-cache source cache)
                   (core/-attach-cache element cache)))
    (-resolve-refs [_ expression-defs]
      (index-of-op (core/-resolve-refs source expression-defs)
                   (core/-resolve-refs element expression-defs)))
    (-resolve-params [_ parameters]
      (index-of-op (core/-resolve-params source parameters)
                   (core/-resolve-params element parameters)))
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
           -1))))
    (-form [_]
      (list 'index-of (core/-form source) (core/-form element)))))

(defmethod core/compile* :elm.compiler.type/index-of
  [context {:keys [source element]}]
  (let [source (core/compile* context source)
        element (core/compile* context element)]
    (index-of-op source element)))

;; 20.18. Last
;;
;; TODO: orderBy
(defn- last-op [source]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (last-op (core/-attach-cache source cache)))
    (-resolve-refs [_ expression-defs]
      (last-op (core/-resolve-refs source expression-defs)))
    (-resolve-params [_ parameters]
      (last-op (core/-resolve-params source parameters)))
    (-eval [_ context resource scopes]
      (peek (core/-eval source context resource scopes)))
    (-form [_]
      (list 'last (core/-form source)))))

(defmethod core/compile* :elm.compiler.type/last
  [context {:keys [source]}]
  (let [source (core/compile* context source)]
    (if (core/static? source)
      (peek source)
      (last-op source))))

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
        start-index (core/compile* context start-index)
        end-index (core/compile* context end-index)]
    (reify core/Expression
      (-static [_]
        false)
      (-eval [_ context resource scopes]
        (when-let [source (core/-eval source context resource scopes)]
          (let [start-index (or (core/-eval start-index context resource scopes) 0)
                end-index (or (core/-eval end-index context resource scopes) (count source))]
            (if (or (neg? start-index) (< end-index start-index))
              []
              (subvec source start-index end-index)))))
      (-form [_]
        (list 'slice (core/-form source) (core/-form start-index) (core/-form end-index))))))

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
         (let [comp (queries/comparator direction)]
           (reify core/Expression
             (-static [_]
               false)
             (-eval [_ context resource scopes]
               (when-let [source (core/-eval source context resource scopes)]
                 (sort-by identity comp source)))
             (-form [_]
               (list 'sort (core/-form source) (keyword direction)))))))
     source
     sort-by-items)))

;; 20.28. Times
(defbinop times [list1 list2]
  (transduce
   (mapcat #(eduction (map (partial merge %)) list1))
   (completing (fnil conj []))
   nil
   list2))
