(ns blaze.elm.compiler.query
  (:require
    [blaze.elm.compiler.protocols :refer [Expression -eval expr?]]
    [clojure.spec.alpha :as s])
  (:import
    [clojure.core Eduction]))


(s/fdef with-xform-factory
  :args (s/cat :with-clause fn?))

(defn with-xform-factory [with-clause]
  (fn create-with-xform [context resource scope]
    (let [with-clause (with-clause context resource scope)]
      (filter #(with-clause context resource %)))))


(defrecord WhereTransducerExpression [expr]
  Expression
  (-eval [_ context resource _]
    (filter #(-eval expr context resource %))))


(s/fdef where-xform-expr
  :args (s/cat :expr expr?))

(defn where-xform-expr [expr]
  (->WhereTransducerExpression expr))


(defrecord ReturnTransducerExpression [expr]
  Expression
  (-eval [_ context resource _]
    (map #(-eval expr context resource %))))


(defrecord DistinctTransducerExpression []
  Expression
  (-eval [_ _ _ _]
    (distinct)))


(defrecord ComposedDistinctTransducerExpression [expr]
  Expression
  (-eval [_ context resource scope]
    (comp
      (-eval expr context resource scope)
      (distinct))))


(s/fdef return-xform-expr
  :args (s/cat :expr (s/nilable expr?) :distinct boolean?))

(defn return-xform-expr [expr distinct]
  (if (some? expr)
    (if distinct
      (-> (->ReturnTransducerExpression expr)
          (->ComposedDistinctTransducerExpression))
      (->ReturnTransducerExpression expr))
    (when distinct
      (->DistinctTransducerExpression))))


(defrecord ComposedTransducerExpression [factories]
  Expression
  (-eval [_ context resource scope]
    (transduce (map #(-eval % context resource scope)) comp factories)))


(defn- comp-xform-factories [factories]
  (->ComposedTransducerExpression factories))


(s/fdef xform-expr
  :args (s/cat :with-xform-factories (s/every expr? :kind vector?)
               :where-xform-expr expr?
               :return-xform-expr expr?))

(defn xform-expr
  [with-xform-factories where-xform-expr return-xform-expr]
  (if (some? where-xform-expr)
    (if (seq with-xform-factories)
      (if (some? return-xform-expr)
        (comp-xform-factories
          (conj
            (into [where-xform-expr] with-xform-factories)
            return-xform-expr))
        (comp-xform-factories (into [where-xform-expr] with-xform-factories)))
      (if (some? return-xform-expr)
        (comp-xform-factories [where-xform-expr return-xform-expr])
        where-xform-expr))
    (if (seq with-xform-factories)
      (if (some? return-xform-expr)
        (comp-xform-factories (conj with-xform-factories return-xform-expr))
        (comp-xform-factories with-xform-factories))
      (when (some? return-xform-expr)
        return-xform-expr))))


(defrecord EductionQueryExpression [xform-expr source]
  Expression
  (-eval [_ context resource scope]
    (Eduction.
      (-eval xform-expr context resource scope)
      (-eval source context resource scope))))


(s/fdef eduction-expr
  :args (s/cat :xform-expr expr? :source expr?))

(defn eduction-expr [xform-expr source]
  (->EductionQueryExpression xform-expr source))


(defrecord IntoVectorQueryExpression [xform-expr source]
  Expression
  (-eval [_ context resource scope]
    (into
      []
      (-eval xform-expr context resource scope)
      (-eval source context resource scope))))


(s/fdef into-vector-expr
  :args (s/cat :xform-expr expr? :source expr?))

(defn into-vector-expr [xform-expr source]
  (->IntoVectorQueryExpression xform-expr source))
