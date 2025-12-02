(ns blaze.elm.compiler.queries
  "10. Queries

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:refer-clojure :exclude [comparator sort-by str])
  (:require
   [blaze.anomaly :refer [if-ok]]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.macros :refer [reify-expr]]
   [blaze.elm.protocols :as p]
   [blaze.elm.resource :as cr]
   [blaze.fhir.spec]
   [blaze.util :refer [str]]
   [cognitect.anomalies :as anom]
   [taoensso.timbre :as log])
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

(defn- where-search-param-xform-factory [matcher]
  (reify XformFactory
    (-create [_ {:keys [db]} _ _]
     ;; TODO: give the matcher the cr/handle function
      (comp (map cr/handle)
            (d/matcher-transducer db matcher)
            (map #(cr/mk-resource db %))))
    (-form [_]
      `(~'matcher ~(d/matcher-clauses matcher)))))

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
  ([relationship-xform-factories where-xform-factory]
   (if (some? where-xform-factory)
     (if (seq relationship-xform-factories)
       (composed-xform-factory
        (into [where-xform-factory] relationship-xform-factories))
       where-xform-factory)
     (cond
       (< 1 (count relationship-xform-factories))
       (composed-xform-factory relationship-xform-factories)
       (seq relationship-xform-factories)
       (first relationship-xform-factories))))
  ([relationship-xform-factories where-xform-factory return-xform-factory]
   (if (some? where-xform-factory)
     (if (seq relationship-xform-factories)
       (composed-xform-factory
        (conj
         (into [where-xform-factory] relationship-xform-factories)
         return-xform-factory))
       (composed-xform-factory [where-xform-factory return-xform-factory]))
     (if (seq relationship-xform-factories)
       (composed-xform-factory (conj relationship-xform-factories return-xform-factory))
       return-xform-factory))))

(defn- medication-source-type [[name & args]]
  (when (and (= 'retrieve name)
             (= 1 (count args))
             (#{"MedicationAdministration"
                "MedicationStatement"}
              (first args)))
    (first args)))

(defn- contains-medication-refs [scope lhs rhs]
  (when (and (every? string? lhs)
             (= (list 'call "ToString" (list :reference (list :medication scope))) rhs))
    lhs))

(defn- filter-medication-refs [[_ [scope] [name & args]]]
  (when (= 'contains name)
    (apply contains-medication-refs scope args)))

(defn- where-medication-refs [[name arg]]
  (when (= 'filter name)
    (filter-medication-refs arg)))

(defn- eduction-expr [xform-factory source]
  (reify-expr core/Expression
    (-resolve-refs [_ expression-defs]
      (eduction-expr (-resolve-refs xform-factory expression-defs)
                     (core/-resolve-refs source expression-defs)))
    (-resolve-params [_ parameters]
      (eduction-expr (-resolve-params xform-factory parameters)
                     (core/-resolve-params source parameters)))
    (-optimize [expr db]
      (let [source (core/-optimize source db)]
        (if (= [] source)
          []
          (if-let [source-type (medication-source-type (core/-form source))]
            (if-let [medication-refs (where-medication-refs (-form xform-factory))]
              (if (empty? medication-refs)
                []
                (if-ok [matcher (d/compile-type-matcher db source-type [(into ["medication"] medication-refs)])]
                  (eduction-expr (where-search-param-xform-factory matcher) source)
                  (fn [{::anom/keys [message]}]
                    (log/warn "Error while trying to optimize a query expression:" message)
                    expr)))
              expr)
            expr))))
    (-eval [_ context resource scope]
      (coll/eduction
       (-create xform-factory context resource scope)
       (core/-eval source context resource scope)))
    (-form [_]
      `(~'eduction-query ~(-form xform-factory) ~(core/-form source)))))

(defn- into-vector-expr [xform-factory source]
  (reify-expr core/Expression
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
    [x y]
    (cond
      (p/less x y) -1
      (p/greater x y) 1
      (and (nil? x) (some? y)) -1
      (and (nil? y) (some? x)) 1
      :else 0)))

(def asc-comparator (->AscComparator))

(deftype DescComparator []
  Comparator
  (compare [_ x y]
    (cond
      (p/less x y) 1
      (p/greater x y) -1
      (and (nil? x) (some? y)) 1
      (and (nil? y) (some? x)) -1
      :else 0)))

(def ^:private desc-comparator (->DescComparator))

(defn comparator [direction]
  (if (#{"desc" "descending"} direction) desc-comparator asc-comparator))

(defn sort-by [f direction coll]
  (clojure.core/sort-by f (comparator direction) coll))

(defn sort-by-item-form [sort-by-item]
  (if-let [expr (:expression sort-by-item)]
    [(symbol (:direction sort-by-item)) (core/-form expr)]
    (symbol (:direction sort-by-item))))

(defn- xform-sort-expr [xform-factory source sort-by-item]
  (reify-expr core/Expression
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
              (partial core/-eval expr context resource)
              identity)
            (:direction sort-by-item))
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
                                   (when distinct (distinct-xform-factory)))
          xform-factory (if return-xform-factory
                          (xform-factory relationship-xform-factories where-xform-factory return-xform-factory)
                          (xform-factory relationship-xform-factories where-xform-factory))
          sort-by-items (mapv #(compile-sort-by-item context %) sort-by-items)]
      (if (empty? sort-by-items)
        (if xform-factory
          (if (contains? optimizations :first)
            (eduction-expr xform-factory source)
            (into-vector-expr xform-factory source))
          source)
        (xform-sort-expr (or xform-factory (distinct-xform-factory)) source (first sort-by-items))))
    (throw (Exception. (str "Unsupported number of " (count sources) " sources in query.")))))

;; 10.3. AliasRef
(defmethod core/compile* :elm.compiler.type/alias-ref
  [_ {:keys [name]}]
  (reify-expr core/Expression
    (-eval [_ _ _ scopes]
      (get scopes name))
    (-form [_]
      `(~'alias-ref ~(symbol name)))))

;; 10.7 IdentifierRef
(defmethod core/compile* :elm.compiler.type/identifier-ref
  [_ {:keys [name]}]
  (let [key (keyword name)]
    (reify-expr core/Expression
      (-eval [_ _ _ value]
        (p/get value key))
      (-form [_]
        `(~key ~'default)))))

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
