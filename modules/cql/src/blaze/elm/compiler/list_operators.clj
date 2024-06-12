(ns blaze.elm.compiler.list-operators
  "20. List Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.anomaly :as ba]
   [blaze.coll.core :as coll]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.macros :refer [defbinop defunop reify-expr]]
   [blaze.elm.compiler.queries :as queries]
   [blaze.elm.protocols :as p]
   [blaze.util :refer [conj-vec]]
   [cognitect.anomalies :as anom])
  (:import
   [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

;; 20.1. List
(defn list-op [elements]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper-list list-op cache elements))
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
    (reify-expr core/Expression
      (-eval [_ _ _ scopes]
        (get scopes scope))
      (-form [_]
        (list 'current scope)))
    (reify-expr core/Expression
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
(defn- scoped-filter-op [source condition scope]
  (reify-expr core/Expression
    (-resolve-refs [_ expression-defs]
      (scoped-filter-op
       (core/-resolve-refs source expression-defs)
       (core/-resolve-refs condition expression-defs)
       scope))
    (-resolve-params [_ parameters]
      (scoped-filter-op
       (core/-resolve-params source parameters)
       (core/-resolve-params condition parameters)
       scope))
    (-eval [_ context resource scopes]
      (when-let [source (core/-eval source context resource scopes)]
        (filterv
         (fn [x]
           (core/-eval condition context resource (assoc scopes scope x)))
         source)))
    (-form [_]
      (list 'filter (core/-form source) (core/-form condition) scope))))

(defn- filter-op [source condition]
  (reify-expr core/Expression
    (-resolve-refs [_ expression-defs]
      (filter-op
       (core/-resolve-refs source expression-defs)
       (core/-resolve-refs condition expression-defs)))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper filter-op parameters source condition))
    (-eval [_ context resource scopes]
      (when-let [source (core/-eval source context resource scopes)]
        (filterv (partial core/-eval condition context resource) source)))
    (-form [_]
      (list 'filter (core/-form source) (core/-form condition)))))

(defmethod core/compile* :elm.compiler.type/filter
  [context {:keys [source condition scope]}]
  (let [source (core/compile* context source)
        condition (core/compile* context condition)]
    (if scope
      (scoped-filter-op source condition scope)
      (filter-op source condition))))

;; 20.10. First
;;
;; TODO: orderBy
(defunop first
  {:optimizations #{:first :non-distinct}
   :operand-key :source}
  [source]
  (coll/first source))

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
(defn- scoped-for-each [source element scope]
  (reify-expr core/Expression
    (-resolve-refs [_ expression-defs]
      (scoped-for-each
       (core/-resolve-refs source expression-defs)
       (core/-resolve-refs element expression-defs)
       scope))
    (-resolve-params [_ parameters]
      (scoped-for-each
       (core/-resolve-params source parameters)
       (core/-resolve-params element parameters)
       scope))
    (-eval [_ context resource scopes]
      (when-let [source (core/-eval source context resource scopes)]
        (mapv
         (fn [x]
           (core/-eval element context resource (assoc scopes scope x)))
         source)))
    (-form [_]
      (list 'for-each (core/-form source) (core/-form element) scope))))

(defn- for-each [source element]
  (reify-expr core/Expression
    (-resolve-refs [_ expression-defs]
      (for-each
       (core/-resolve-refs source expression-defs)
       (core/-resolve-refs element expression-defs)))
    (-resolve-params [_ parameters]
      (for-each
       (core/-resolve-params source parameters)
       (core/-resolve-params element parameters)))
    (-eval [_ context resource scopes]
      (when-let [source (core/-eval source context resource scopes)]
        (mapv (partial core/-eval element context resource) source)))
    (-form [_]
      (list 'for-each (core/-form source) (core/-form element)))))

(defmethod core/compile* :elm.compiler.type/for-each
  [context {:keys [source element scope]}]
  (let [source (core/compile* context source)
        element (core/compile* context element)]
    (if scope
      (scoped-for-each source element scope)
      (for-each source element))))

;; 20.16. IndexOf
(defn- index-of-op [source element]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper index-of-op cache source element))
    (-resolve-refs [_ expression-defs]
      (index-of-op (core/-resolve-refs source expression-defs)
                   (core/-resolve-refs element expression-defs)))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper index-of-op parameters source element))
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
(defunop last
  {:operand-key :source}
  [source]
  (peek source))

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
(defn- slice-op [source start-index end-index]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper slice-op cache source start-index end-index))
    (-resolve-refs [_ expression-defs]
      (slice-op (core/-resolve-refs source expression-defs)
                (core/-resolve-refs start-index expression-defs)
                (core/-resolve-refs end-index expression-defs)))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper slice-op parameters source start-index end-index))
    (-eval [_ context resource scopes]
      (when-let [source (core/-eval source context resource scopes)]
        (let [start-index (or (core/-eval start-index context resource scopes) 0)
              end-index (or (core/-eval end-index context resource scopes) (count source))]
          (if (or (neg? start-index) (< end-index start-index))
            []
            (subvec source start-index end-index)))))
    (-form [_]
      (list 'slice (core/-form source) (core/-form start-index) (core/-form end-index)))))

(defmethod core/compile* :elm.compiler.type/slice
  [context {:keys [source] start-index :startIndex end-index :endIndex}]
  (let [source (core/compile* context source)
        start-index (core/compile* context start-index)
        end-index (core/compile* context end-index)]
    (slice-op source start-index end-index)))

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
           (reify-expr core/Expression
             ;; TODO: other methods
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
   (completing conj-vec)
   nil
   list2))
