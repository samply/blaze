(ns blaze.elm.compiler.queries
  "10. Queries

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:refer-clojure :exclude [comparator])
  (:require
   [blaze.coll.core :as coll]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.structured-values :as structured-values]
   [blaze.elm.protocols :as p]
   [blaze.fhir.spec])
  (:import
   [java.util Comparator]))

(set! *warn-on-reflection* true)

(defprotocol XformFactory
  (-create [_ context resource scope]
    "Creates a xform which filters and/or shapes query sources.")
  (-resolve-refs [_ expression-defs])
  (-resolve-params [_ parameters])
  (-form [_]))

(defn- where-xform-factory [alias expr]
  (reify XformFactory
    (-create [_ context resource scope]
      (filter #(core/-eval expr context resource (assoc scope alias %))))
    (-resolve-refs [_ expression-defs]
      (where-xform-factory alias (core/-resolve-refs expr expression-defs)))
    (-resolve-params [_ parameters]
      (where-xform-factory alias (core/-resolve-params expr parameters)))
    (-form [_]
      `(~'filter (~'fn [~(symbol alias)] ~(core/-form expr))))))

(defn- return-xform-factory* [alias expr]
  (reify XformFactory
    (-create [_ context resource scope]
      (map #(core/-eval expr context resource (assoc scope alias %))))
    (-resolve-refs [_ expression-defs]
      (return-xform-factory* alias (core/-resolve-refs expr expression-defs)))
    (-resolve-params [_ parameters]
      (return-xform-factory* alias (core/-resolve-params expr parameters)))
    (-form [_]
      `(~'map (~'fn [~(symbol alias)] ~(core/-form expr))))))

(defn- distinct-xform-factory []
  (reify XformFactory
    (-create [_ _ _ _]
      (distinct))
    (-resolve-refs [this _]
      this)
    (-resolve-params [this _]
      this)
    (-form [_]
      'distinct)))

(defn- composed-distinct-xform-factory [xform-factory]
  (reify XformFactory
    (-create [_ context resource scope]
      (comp
       (-create xform-factory context resource scope)
       (distinct)))
    (-resolve-refs [_ expression-defs]
      (composed-distinct-xform-factory (-resolve-refs xform-factory expression-defs)))
    (-resolve-params [_ parameters]
      (composed-distinct-xform-factory (-resolve-params xform-factory parameters)))
    (-form [_]
      `(~'comp ~(-form xform-factory) ~'distinct))))

(defn- return-xform-factory [alias distinct expr]
  (if distinct
    (-> (return-xform-factory* alias expr)
        (composed-distinct-xform-factory))
    (return-xform-factory* alias expr)))

(defn- composed-xform-factory [factories]
  (reify XformFactory
    (-create [_ context resource scope]
      (transduce (map #(-create % context resource scope)) comp factories))
    (-resolve-refs [_ expression-defs]
      (composed-xform-factory (mapv #(-resolve-refs % expression-defs) factories)))
    (-resolve-params [_ parameters]
      (composed-xform-factory (mapv #(-resolve-params % parameters) factories)))
    (-form [_]
      `(~'comp ~@(map -form factories)))))

(defn- xform-factory
  [relationship-xform-factories where-xform-factory return-xform-factory]
  (if (some? where-xform-factory)
    (if (seq relationship-xform-factories)
      (composed-xform-factory
       (conj
        (into [where-xform-factory] relationship-xform-factories)
        return-xform-factory))
      (composed-xform-factory [where-xform-factory return-xform-factory]))
    (if (seq relationship-xform-factories)
      (composed-xform-factory (conj relationship-xform-factories return-xform-factory))
      return-xform-factory)))

(defn- eduction-expr [xform-factory source]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (eduction-expr xform-factory (core/-attach-cache source cache)))
    (-resolve-refs [_ expression-defs]
      (eduction-expr (-resolve-refs xform-factory expression-defs)
                     (core/-resolve-refs source expression-defs)))
    (-resolve-params [_ parameters]
      (eduction-expr (-resolve-params xform-factory parameters)
                     (core/-resolve-params source parameters)))
    (-eval [_ context resource scope]
      (coll/eduction
       (-create xform-factory context resource scope)
       (core/-eval source context resource scope)))
    (-form [_]
      `(~'eduction-query ~(-form xform-factory) ~(core/-form source)))))

(defn- into-vector-expr [xform-factory source]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (into-vector-expr xform-factory (core/-attach-cache source cache)))
    (-resolve-refs [_ expression-defs]
      (into-vector-expr (-resolve-refs xform-factory expression-defs)
                        (core/-resolve-refs source expression-defs)))
    (-resolve-params [_ parameters]
      (into-vector-expr (-resolve-params xform-factory parameters)
                        (core/-resolve-params source parameters)))
    (-eval [_ context resource scope]
      (into
       []
       (-create xform-factory context resource scope)
       (core/-eval source context resource scope)))
    (-form [_]
      `(~'vector-query ~(-form xform-factory) ~(core/-form source)))))

(deftype AscComparator []
  Comparator
  (compare [_ x y]
    (let [c (p/less x y)]
      (cond
        (true? c) -1
        (false? c) 1
        (nil? x) -1
        (nil? y) 1
        :else 0))))

(def asc-comparator (->AscComparator))

(deftype DescComparator []
  Comparator
  (compare [_ x y]
    (let [c (p/less x y)]
      (cond
        (true? c) 1
        (false? c) -1
        (nil? x) 1
        (nil? y) -1
        :else 0))))

(def ^:private desc-comparator (->DescComparator))

(defn comparator [direction]
  (if (#{"desc" "descending"} direction) desc-comparator asc-comparator))

(defn sort-by-item-form [sort-by-item]
  (if-let [expr (:expression sort-by-item)]
    [(symbol (:direction sort-by-item)) (core/-form expr)]
    (symbol (:direction sort-by-item))))

(defn- sort-expr [source sort-by-item]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (sort-expr (core/-attach-cache source cache)
                 (update sort-by-item :expression core/-attach-cache cache)))
    (-resolve-refs [_ expression-defs]
      (sort-expr (core/-resolve-refs source expression-defs)
                 (update sort-by-item :expression core/-resolve-refs expression-defs)))
    (-resolve-params [_ parameters]
      (sort-expr (core/-resolve-params source parameters)
                 (update sort-by-item :expression core/-resolve-params parameters)))
    (-eval [_ context resource scope]
     ;; TODO: build a comparator of all sort by items
      (->> (vec (core/-eval source context resource scope))
           (sort-by
            (if-let [expr (:expression sort-by-item)]
              #(core/-eval expr context resource %)
              identity)
            (comparator (:direction sort-by-item)))
           (vec)))
    (-form [_]
      `(~'sorted-vector-query ~(core/-form source)
                              ~(sort-by-item-form sort-by-item)))))

(defn- xform-sort-expr [xform-factory source sort-by-item]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (xform-sort-expr xform-factory
                       (core/-attach-cache source cache)
                       (update sort-by-item :expression core/-attach-cache cache)))
    (-resolve-refs [_ expression-defs]
      (xform-sort-expr (-resolve-refs xform-factory expression-defs)
                       (core/-resolve-refs source expression-defs)
                       (update sort-by-item :expression core/-resolve-refs expression-defs)))
    (-resolve-params [_ parameters]
      (xform-sort-expr (-resolve-params xform-factory parameters)
                       (core/-resolve-params source parameters)
                       (update sort-by-item :expression core/-resolve-params parameters)))
    (-eval [_ context resource scope]
     ;; TODO: build a comparator of all sort by items
      (->> (into
            []
            (-create xform-factory context resource scope)
            (core/-eval source context resource scope))
           (sort-by
            (if-let [expr (:expression sort-by-item)]
              #(core/-eval expr context resource %)
              identity)
            (comparator (:direction sort-by-item)))
           (vec)))
    (-form [_]
      `(~'sorted-vector-query ~(-form xform-factory) ~(core/-form source)
                              ~(sort-by-item-form sort-by-item)))))

(declare compile-relationship-clause)

;; 10.1. Query
(defmulti compile-sort-by-item (fn [_ {:keys [type]}] type))

(defmethod compile-sort-by-item "ByExpression"
  [context sort-by-item]
  (update sort-by-item :expression #(core/compile* context %)))

(defmethod compile-sort-by-item :default
  [_ sort-by-item]
  sort-by-item)

(defmethod core/compile* :elm.compiler.type/query
  [{:keys [optimizations] :as context}
   {sources :source
    relationships :relationship
    :keys [where]
    {return :expression :keys [distinct] :or {distinct true}} :return
    {sort-by-items :by} :sort}]
  (if (= 1 (count sources))
    (let [{:keys [expression alias]} (first sources)
          context (dissoc context :optimizations)
          source (core/compile* context expression)
          relationship-xform-factories (mapv #(compile-relationship-clause context alias %) relationships)
          where-xform-factory (some->> where (core/compile* context) (where-xform-factory alias))
          distinct (if (contains? optimizations :non-distinct) false distinct)
          return-xform-factory (or (some->> return (core/compile* context) (return-xform-factory alias distinct))
                                   (distinct-xform-factory))
          xform-factory (xform-factory relationship-xform-factories where-xform-factory return-xform-factory)
          sort-by-items (mapv #(compile-sort-by-item context %) sort-by-items)]
      (if (empty? sort-by-items)
        (if xform-factory
          (if (contains? optimizations :first)
            (eduction-expr xform-factory source)
            (into-vector-expr xform-factory source))
          source)
        (if xform-factory
          (xform-sort-expr xform-factory source (first sort-by-items))
          (sort-expr source (first sort-by-items)))))
    (throw (Exception. (str "Unsupported number of " (count sources) " sources in query.")))))

;; 10.3. AliasRef
(defrecord AliasRefExpression [key]
  core/Expression
  (-static [_]
    false)
  (-attach-cache [expr _]
    expr)
  (-resolve-refs [expr _]
    expr)
  (-resolve-params [expr _]
    expr)
  (-eval [_ _ _ scopes]
    (get scopes key))
  (-form [_]
    `(~'alias-ref ~(symbol key))))

(defmethod core/compile* :elm.compiler.type/alias-ref
  [_ {:keys [name]}]
  (->AliasRefExpression name))

;; 10.7 IdentifierRef
(defmethod core/compile* :elm.compiler.type/identifier-ref
  [_ {:keys [name]}]
  (structured-values/->SingleScopePropertyExpression (keyword name)))

;; 10.14. With
;; 10.15. Without
(defn- relationship-clause-xform-factory [lhs-alias rhs-alias rhs such-that exists-fn form-sym]
  (reify XformFactory
    (-create [_ context resource scope]
      (filter
       (fn [lhs-item]
         (let [scope (assoc scope lhs-alias lhs-item)]
           (exists-fn
            #(core/-eval such-that context resource (assoc scope rhs-alias %))
            (core/-eval rhs context resource scope))))))
    (-resolve-refs [_ expression-defs]
      (relationship-clause-xform-factory
       lhs-alias rhs-alias (core/-resolve-refs rhs expression-defs)
       (core/-resolve-refs such-that expression-defs) exists-fn form-sym))
    (-resolve-params [_ parameters]
      (relationship-clause-xform-factory
       lhs-alias rhs-alias (core/-resolve-params rhs parameters)
       (core/-resolve-params such-that parameters) exists-fn form-sym))
    (-form [_]
      `(~'filter
        (~'fn [~(symbol lhs-alias)]
              (~form-sym
               (~'fn [~(symbol rhs-alias)]
                     ~(core/-form such-that))
               ~(core/-form rhs)))))))

(defn compile-relationship-clause
  "We use the terms `lhs` and `rhs` for left-hand-side and right-hand-side of
  the semi-join here.

  Returns an XformFactory."
  {:arglists '([context lhs-alias clause])}
  [context lhs-alias {:keys [type] rhs-alias :alias rhs :expression such-that :suchThat}]
  (let [rhs (core/compile* context rhs)
        such-that (core/compile* context such-that)
        exists-fn (if (= "With" type) coll/some (comp not coll/some))
        form-sym (if (= "With" type) 'exists 'not-exists)]
    (relationship-clause-xform-factory lhs-alias rhs-alias rhs such-that exists-fn form-sym)))
