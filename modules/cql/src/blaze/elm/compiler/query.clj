(ns blaze.elm.compiler.query
  (:require
    [blaze.coll.core :as coll]
    [blaze.elm.compiler.protocols :refer [Expression -eval]]
    [blaze.elm.expression-spec]
    [blaze.elm.protocols :as p]
    [blaze.fhir.spec])
  (:import
    [java.util Comparator])
  (:refer-clojure :exclude [comparator]))


(set! *warn-on-reflection* true)


(defprotocol XformFactory
  (-create [_ context resource]
    "Creates an xform which filters and/or shapes query sources."))


(defrecord WithXformFactory
  [rhs rhs-operand such-that lhs-operand single-query-scope]
  XformFactory
  (-create [_ context resource]
    (let [rhs (-eval rhs context resource nil)
          indexer #(-eval rhs-operand context resource %)]
      (if (some? such-that)
        (let [index (group-by indexer rhs)]
          (filter
            (fn eval-with-clause [lhs-entity]
              (when-let [rhs-entities (some->> (-eval lhs-operand context
                                                      resource lhs-entity)
                                               (get index))]
                (some
                  #(-eval such-that context resource
                          {single-query-scope lhs-entity alias %})
                  rhs-entities)))))
        (let [index (into #{} (map indexer) rhs)]
          (filter
            (fn eval-with-clause [lhs-entity]
              (some->> (-eval lhs-operand context resource lhs-entity)
                       (contains? index)))))))))


(defn with-xform-factory [create-with-clause]
  (fn create-with-xform [context resource scope]
    (let [with-clause (create-with-clause context resource scope)]
      (filter #(with-clause context resource %)))))


(defrecord WhereXformFactory [expr]
  XformFactory
  (-create [_ context resource]
    (filter #(-eval expr context resource %))))


(defn where-xform-factory [expr]
  (->WhereXformFactory expr))


(defrecord ReturnXformFactory [expr]
  XformFactory
  (-create [_ context resource]
    (map #(-eval expr context resource %))))


(defrecord DistinctXformFactory []
  XformFactory
  (-create [_ _ _]
    (distinct)))


(defrecord ComposedDistinctXformFactory [xform-factory]
  XformFactory
  (-create [_ context resource]
    (comp
      (-create xform-factory context resource)
      (distinct))))


(defn return-xform-factory [expr distinct]
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
    (transduce (map #(-create % context resource)) comp factories)))


(defn xform-factory
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
  Expression
  (-eval [_ context resource scope]
    (coll/eduction
      (-create xform-factory context resource)
      (-eval source context resource scope))))


(defn eduction-expr [xform-factory source]
  (->EductionQueryExpression xform-factory source))


(defrecord IntoVectorQueryExpression [xform-factory source]
  Expression
  (-eval [_ context resource scope]
    (into
      []
      (-create xform-factory context resource)
      (-eval source context resource scope))))


(defn into-vector-expr [xform-factory source]
  (->IntoVectorQueryExpression xform-factory source))


(deftype AscComparator []
  Comparator
  (compare [_ x y]
    (let [less (p/less x y)]
      (cond
        (true? less) -1
        (false? less) 1
        (nil? x) -1
        (nil? y) 1
        :else 0))))


(def asc-comparator (->AscComparator))


(deftype DescComparator []
  Comparator
  (compare [_ x y]
    (let [less (p/less x y)]
      (cond
        (true? less) 1
        (false? less) -1
        (nil? x) 1
        (nil? y) -1
        :else 0))))


(def ^:private desc-comparator (->DescComparator))


(defn comparator [direction]
  (if (#{"desc" "descending"} direction) desc-comparator asc-comparator))


(defrecord SortQueryExpression [source sort-by-item]
  Expression
  (-eval [_ context resource scope]
    ;; TODO: build a comparator of all sort by items
    (->> (into [] (-eval source context resource scope))
         (sort-by
           (if-let [expr (:expression sort-by-item)]
             #(-eval expr context resource %)
             identity)
           (comparator (:direction sort-by-item)))
         (vec))))


(defn sort-expr [source sort-by-item]
  (->SortQueryExpression source sort-by-item))


(defrecord XformSortQueryExpression [xform-factory source sort-by-item]
  Expression
  (-eval [_ context resource scope]
    ;; TODO: build a comparator of all sort by items
    (->> (into
           []
           (-create xform-factory context resource)
           (-eval source context resource scope))
         (sort-by
           (if-let [expr (:expression sort-by-item)]
             #(-eval expr context resource %)
             identity)
           (comparator (:direction sort-by-item)))
         (vec))))


(defn xform-sort-expr [xform-factory source sort-by-item]
  (->XformSortQueryExpression xform-factory source sort-by-item))
