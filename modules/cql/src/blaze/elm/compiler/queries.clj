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
  (-create [_ context resource]
    "Creates a xform which filters and/or shapes query sources.")
  (-form [_]))


(defrecord WithXformFactory
  [rhs rhs-operand such-that lhs-operand single-query-scope]
  XformFactory
  (-create [_ context resource]
    (let [rhs (core/-eval rhs context resource nil)
          indexer #(core/-eval rhs-operand context resource %)]
      (if (some? such-that)
        (let [index (group-by indexer rhs)]
          (filter
            (fn eval-with-clause [lhs-entity]
              (when-let [rhs-entities (some->> (core/-eval lhs-operand context
                                                           resource lhs-entity)
                                               (get index))]
                (some
                  #(core/-eval such-that context resource
                               {single-query-scope lhs-entity alias %})
                  rhs-entities)))))
        (let [index (into #{} (map indexer) rhs)]
          (filter
            (fn eval-with-clause [lhs-entity]
              (some->> (core/-eval lhs-operand context resource lhs-entity)
                       (contains? index)))))))))


#_(defn- with-xform-factory [create-with-clause]
    (fn create-with-xform [context resource scope]
      (let [with-clause (create-with-clause context resource scope)]
        (filter #(with-clause context resource %)))))


(defrecord WhereXformFactory [expr]
  XformFactory
  (-create [_ context resource]
    (filter #(core/-eval expr context resource %)))
  (-form [_]
    `(~'where ~(core/-form expr))))


(defn- where-xform-factory [expr]
  (->WhereXformFactory expr))


(defrecord ReturnXformFactory [expr]
  XformFactory
  (-create [_ context resource]
    (map #(core/-eval expr context resource %))))


(defrecord DistinctXformFactory []
  XformFactory
  (-create [_ _ _]
    (distinct))
  (-form [_]
    'distinct))


(defrecord ComposedDistinctXformFactory [xform-factory]
  XformFactory
  (-create [_ context resource]
    (comp
      (-create xform-factory context resource)
      (distinct))))


(defn- return-xform-factory [expr distinct]
  (if (some? expr)
    (if distinct
      (-> (->ReturnXformFactory expr)
          (->ComposedDistinctXformFactory))
      (->ReturnXformFactory expr))
    (when distinct
      (->DistinctXformFactory))))


(defrecord ComposedXformFactory [factories]
  XformFactory
  (-create [_ context resource]
    (transduce (map #(-create % context resource)) comp factories))
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
      (-create xform-factory context resource)
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
      (-create xform-factory context resource)
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
    (->> (into [] (core/-eval source context resource scope))
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
           (-create xform-factory context resource)
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
;;
;; The Query operator represents a clause-based query. The result of the query
;; is determined by the type of sources included, as well as the clauses used in
;; the query.
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
          context (assoc context :life/single-query-scope alias)
          with-equiv-clauses (filter (comp #{"WithEquiv"} :type) relationships)
          with-xform-factories (map #(compile-with-equiv-clause context %) with-equiv-clauses)
          where-xform-factory (some->> where (core/compile* context) (where-xform-factory))
          distinct (if (contains? optimizations :non-distinct) false distinct)
          return-xform-factory (return-xform-factory (some->> return (core/compile* context)) distinct)
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


;; ?.? IdentifierRef
;;
;; The IdentifierRef type defines an expression that references an identifier
;; that is either unresolved, or has been resolved to an attribute in an
;; unambiguous iteration scope such as a sort. Implementations should attempt to
;; resolve the identifier, only throwing an error at compile-time (or run-time
;; for an interpretive system) if the identifier reference cannot be resolved.
(defmethod core/compile* :elm.compiler.type/identifier-ref
  [_ {:keys [name]}]
  (structured-values/->SingleScopePropertyExpression (keyword name)))


;; 10.3. AliasRef
;;
;; The AliasRef expression allows for the reference of a specific source within
;; the context of a query.
(defrecord AliasRefExpression [key]
  core/Expression
  (-eval [_ _ _ scopes]
    (get scopes key)))


(defrecord SingleScopeAliasRefExpression []
  core/Expression
  (-eval [_ _ _ scope]
    scope))


(def single-scope-alias-ref-expression (->SingleScopeAliasRefExpression))


(defmethod core/compile* :elm.compiler.type/alias-ref
  [{:life/keys [single-query-scope]} {:keys [name]}]
  (if (= single-query-scope name)
    single-scope-alias-ref-expression
    (->AliasRefExpression name)))


;; 10.12. With
;;
;; The With clause restricts the elements of a given source to only those
;; elements that have elements in the related source that satisfy the suchThat
;; condition. This operation is known as a semi-join in database languages.
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
  {:arglists '([context with-equiv-clause])}
  [context {:keys [alias] rhs :expression equiv-operands :equivOperand
            such-that :suchThat}]
  (if-let [single-query-scope (:life/single-query-scope context)]
    (if-let [rhs-operand (find-operand-with-alias equiv-operands alias)]
      (if-let [lhs-operand (find-operand-with-alias equiv-operands
                                                    single-query-scope)]
        (let [rhs (core/compile* context rhs)
              rhs-operand (core/compile* (assoc context :life/single-query-scope alias)
                                         rhs-operand)
              lhs-operand (core/compile* context lhs-operand)
              such-that (some->> such-that
                                 (core/compile* (dissoc context :life/single-query-scope)))]
          (->WithXformFactory rhs rhs-operand such-that lhs-operand
                              single-query-scope))
        (throw-anom missing-lhs-operand-anom))
      (throw-anom (missing-rhs-operand-anom alias)))
    (throw-anom
      (ba/incorrect
        (format "Unsupported call without single query scope.")))))


;; TODO 10.13. Without
