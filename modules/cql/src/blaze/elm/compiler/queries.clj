(ns blaze.elm.compiler.queries
  "10. Queries

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:refer-clojure :exclude [comparator])
  (:require
    [blaze.anomaly :as ba :refer [throw-anom]]
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
  (-form [_]))


(defrecord WithXformFactory
  [rhs rhs-operand rhs-alias such-that lhs-operand lhs-alias]
  XformFactory
  (-create [_ context resource scope]
    (let [rhs (core/-eval rhs context resource scope)
          indexer #(core/-eval rhs-operand context resource
                               (assoc scope rhs-alias %))]
      (if (some? such-that)
        (let [index (group-by indexer rhs)]
          (filter
            (fn eval-with-clause [lhs-entity]
              (when-let [rhs-entities (some->> (core/-eval lhs-operand context
                                                           resource lhs-entity)
                                               (get index))]
                (some
                  #(core/-eval such-that context resource
                               (assoc scope lhs-alias lhs-entity rhs-alias %))
                  rhs-entities)))))
        (let [index (into #{} (map indexer) rhs)]
          (filter
            (fn eval-with-clause [lhs-entity]
              (some->> (core/-eval lhs-operand context resource
                                   (assoc scope lhs-alias lhs-entity))
                       (contains? index))))))))
  (-form [_]
    (list 'with (core/-form rhs))))


#_(defn- with-xform-factory [create-with-clause]
    (fn create-with-xform [context resource scope]
      (let [with-clause (create-with-clause context resource scope)]
        (filter #(with-clause context resource %)))))


(defrecord WhereXformFactory [alias expr]
  XformFactory
  (-create [_ context resource scope]
    (filter #(core/-eval expr context resource (assoc scope alias %))))
  (-form [_]
    `(~'where ~(symbol alias) ~(core/-form expr))))


(defn- where-xform-factory [alias expr]
  (->WhereXformFactory alias expr))


(defrecord ReturnXformFactory [alias expr]
  XformFactory
  (-create [_ context resource scope]
    (map #(core/-eval expr context resource (assoc scope alias %))))
  (-form [_]
    `(~'return ~(symbol alias) ~(core/-form expr))))


(defrecord DistinctXformFactory []
  XformFactory
  (-create [_ _ _ _]
    (distinct))
  (-form [_]
    'distinct))


(defrecord ComposedDistinctXformFactory [xform-factory]
  XformFactory
  (-create [_ context resource scope]
    (comp
      (-create xform-factory context resource scope)
      (distinct)))
  (-form [_]
    `(~'distinct ~(-form xform-factory))))


(defn- return-xform-factory [alias expr distinct]
  (if (some? expr)
    (if distinct
      (-> (->ReturnXformFactory alias expr)
          (->ComposedDistinctXformFactory))
      (->ReturnXformFactory alias expr))
    (when distinct
      (->DistinctXformFactory))))


(defrecord ComposedXformFactory [factories]
  XformFactory
  (-create [_ context resource scope]
    (transduce (map #(-create % context resource scope)) comp factories))
  (-form [_]
    `(~'comp ~@(map -form factories))))


(defn- xform-factory
  [with-xform-factories where-xform-factory return-xform-factory]
  (if (some? where-xform-factory)
    (if (seq with-xform-factories)
      (if (some? return-xform-factory)
        (->ComposedXformFactory
          (conj
            (into [where-xform-factory] with-xform-factories)
            return-xform-factory))
        (->ComposedXformFactory (into [where-xform-factory] with-xform-factories)))
      (if (some? return-xform-factory)
        (->ComposedXformFactory [where-xform-factory return-xform-factory])
        where-xform-factory))
    (if (seq with-xform-factories)
      (if (some? return-xform-factory)
        (->ComposedXformFactory (conj with-xform-factories return-xform-factory))
        (->ComposedXformFactory with-xform-factories))
      (when (some? return-xform-factory)
        return-xform-factory))))


(defrecord EductionQueryExpression [xform-factory source]
  core/Expression
  (-eval [_ context resource scope]
    (coll/eduction
      (-create xform-factory context resource scope)
      (core/-eval source context resource scope)))
  (-form [_]
    `(~'eduction-query ~(-form xform-factory) ~(core/-form source))))


(defn- eduction-expr [xform-factory source]
  (->EductionQueryExpression xform-factory source))


(defrecord IntoVectorQueryExpression [xform-factory source]
  core/Expression
  (-eval [_ context resource scope]
    (into
      []
      (-create xform-factory context resource scope)
      (core/-eval source context resource scope)))
  (-form [_]
    `(~'vector-query ~(-form xform-factory) ~(core/-form source))))


(defn- into-vector-expr [xform-factory source]
  (->IntoVectorQueryExpression xform-factory source))


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


(defrecord SortQueryExpression [source sort-by-item]
  core/Expression
  (-eval [_ context resource scope]
    ;; TODO: build a comparator of all sort by items
    (->> (vec (core/-eval source context resource scope))
         (sort-by
           (if-let [expr (:expression sort-by-item)]
             #(core/-eval expr context resource %)
             identity)
           (comparator (:direction sort-by-item)))
         (vec))))


(defn sort-expr [source sort-by-item]
  (->SortQueryExpression source sort-by-item))


(defrecord XformSortQueryExpression [xform-factory source sort-by-item]
  core/Expression
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
         (vec))))


(defn xform-sort-expr [xform-factory source sort-by-item]
  (->XformSortQueryExpression xform-factory source sort-by-item))


(declare compile-with-equiv-clause)


;; 10.1. Query
(defmulti compile-sort-by-item (fn [_ {:keys [type]}] type))


(defmethod compile-sort-by-item "ByExpression"
  [context sort-by-item]
  (update sort-by-item :expression #(core/compile* context %)))


(defmethod compile-sort-by-item :default
  [_ sort-by-item]
  sort-by-item)


(defn- unsupported-with-clause-anom [expr]
  (ba/unsupported
    "Unsupported With clause in query expression."
    :expression expr))


(defn- unsupported-without-clause-anom [expr]
  (ba/unsupported
    "Unsupported Without clause in query expression."
    :expression expr))


(defmethod core/compile* :elm.compiler.type/query
  [{:keys [optimizations] :as context}
   {sources :source
    relationships :relationship
    :keys [where]
    {return :expression :keys [distinct] :or {distinct true}} :return
    {sort-by-items :by} :sort
    :as expr}]
  (when (seq (filter (comp #{"With"} :type) relationships))
    (throw-anom (unsupported-with-clause-anom expr)))
  (when (seq (filter (comp #{"Without"} :type) relationships))
    (throw-anom (unsupported-without-clause-anom expr)))
  (if (= 1 (count sources))
    (let [{:keys [expression alias]} (first sources)
          context (dissoc context :optimizations)
          source (core/compile* context expression)
          with-equiv-clauses (filter (comp #{"WithEquiv"} :type) relationships)
          with-xform-factories (map #(compile-with-equiv-clause context alias %) with-equiv-clauses)
          where-xform-factory (some->> where (core/compile* context) (where-xform-factory alias))
          distinct (if (contains? optimizations :non-distinct) false distinct)
          return-xform-factory (return-xform-factory alias (some->> return (core/compile* context)) distinct)
          xform-factory (xform-factory with-xform-factories where-xform-factory return-xform-factory)
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
(defn- find-operand-with-alias
  "Finds the operand in `expression` that accesses entities with `alias`."
  [operands alias]
  (some #(when (contains? (:life/scopes %) alias) %) operands))


(def ^:private missing-lhs-operand-anom
  (ba/incorrect (format "Unsupported call without left-hand-side operand.")))


(defn- missing-rhs-operand-msg [alias]
  (format "Unsupported call without right-hand-side operand with alias `%s`."
          alias))

(defn- missing-rhs-operand-anom [alias]
  (ba/incorrect (missing-rhs-operand-msg alias)))


(defn compile-with-equiv-clause
  "We use the terms `lhs` and `rhs` for left-hand-side and right-hand-side of
  the semi-join here.

  Returns an XformFactory."
  {:arglists '([context lhs-alias with-equiv-clause])}
  [context
   lhs-alias
   {rhs-alias :alias rhs :expression equiv-operands :equivOperand
    such-that :suchThat}]
  (if-let [rhs-operand (find-operand-with-alias equiv-operands rhs-alias)]
    (if-let [lhs-operand (find-operand-with-alias equiv-operands lhs-alias)]
      (let [rhs (core/compile* context rhs)
            rhs-operand (core/compile* context rhs-operand)
            lhs-operand (core/compile* context lhs-operand)
            such-that (some->> such-that (core/compile* context))]
        (->WithXformFactory rhs rhs-operand rhs-alias such-that lhs-operand lhs-alias))
      (throw-anom missing-lhs-operand-anom))
    (throw-anom (missing-rhs-operand-anom rhs-alias))))


;; TODO 10.15. Without
