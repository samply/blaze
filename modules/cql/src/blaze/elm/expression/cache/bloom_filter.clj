(ns blaze.elm.expression.cache.bloom-filter
  (:refer-clojure :exclude [merge])
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.expression :as expr]
   [blaze.elm.expression.cache.codec :as codec]
   [blaze.elm.expression.cache.codec.form :as form]
   [blaze.elm.resource :as cr]
   [blaze.time :as bt]
   [prometheus.alpha :as prom :refer [defhistogram]]
   [taoensso.timbre :as log])
  (:import
   [blaze.elm.expression.cache.codec BloomFilterContainer]
   [blaze.elm.resource Resource]
   [com.google.common.hash BloomFilter]))

(set! *warn-on-reflection* true)

(defhistogram bloom-filter-bytes
  "Bloom filter sizes in bytes."
  {:namespace "blaze"
   :subsystem "cql_expr_cache"}
  (take 12 (iterate #(* 4 %) 1)))

(defhistogram bloom-filter-creation-duration-seconds
  "Durations in Bloom filter creation."
  {:namespace "blaze"
   :subsystem "cql_expr_cache"}
  (take 16 (iterate #(* 2 %) 0.1)))

(defn might-contain?
  "Returns true if `resource` might have been put in `bloom-filter` or false if
  this is definitely not the case.

  A Bloom filter created on a database state newer than the database of
  `resource` can't be used to draw conclusions about that older database state,
  so it is treated as if it might contain `resource`. Such newer Bloom filters
  are normally already filtered out at attach time via
  `blaze.elm.expression.cache/with-max-t`; this check is the semantic backstop
  that keeps results correct for any caller."
  {:arglists '([bloom-filter resource])}
  [^BloomFilterContainer bloom-filter ^Resource resource]
  (or (< (.-t bloom-filter) (.-lastChangeT resource))
      (< (d/t (.-db resource)) (.-t bloom-filter))
      (.mightContain ^BloomFilter (.-filter bloom-filter) (:id resource))))

(defn merge [bloom-filter-a bloom-filter-b]
  (.merge ^BloomFilterContainer bloom-filter-a bloom-filter-b))

(defn- calc-mem-size [n p]
  (long (/ (* (- n) (Math/log p)) (* (Math/log 2) (Math/log 2)) 8)))

(defn build-bloom-filter [expression t resource-ids]
  (let [n (count resource-ids)
        p (double 0.01)
        filter (BloomFilter/create codec/id-funnel (int (max 10000 n)) p)
        mem-size (calc-mem-size (max 10000 n) p)
        expr-hash (form/hash (pr-str (core/-form expression)))]
    (prom/observe! bloom-filter-bytes mem-size)
    (run! #(.put filter %) resource-ids)
    (BloomFilterContainer. expr-hash t (pr-str (core/display-form expression)) n
                           filter mem-size)))

(defn- calc-bloom-filter [db xform expression]
  (with-open [_ (prom/timer bloom-filter-creation-duration-seconds)]
    (build-bloom-filter
     expression
     (d/t db)
     (into
      []
      (comp (cr/patient-mapper db)
            xform
            (filter (partial expr/eval {:db db :now (bt/offset-date-time)} expression))
            (map :id))
      (d/type-list db "Patient")))))

(defn- create-bloom-filter-msg [expression db]
  (format "Create Bloom filter for expression `%s` evaluating it for %d patients."
          (core/display-form expression) (d/type-total db "Patient")))

(defn- with-db
  "Calls `f` with the current database value of `node` and returns its
  result."
  [node f]
  (ac/join (d/with-db node #(ac/completed-future (f %)))))

(defn create [node expression]
  (with-db node
    (fn [db]
      (log/debug (create-bloom-filter-msg expression db))
      (calc-bloom-filter db identity expression))))

(defn- recreate-bloom-filter-msg [expr-form t db]
  (format "Recreate Bloom filter for expression `%s` last created at t = %d evaluating it for %d patients."
          expr-form t (d/type-total db "Patient")))

(defn recreate
  {:arglists '([node old-bloom-filter expression])}
  [node {::keys [t expr-form] :as old-bloom-filter} expression]
  (with-db node
    (fn [db]
      (log/debug (recreate-bloom-filter-msg expr-form t db))
      (calc-bloom-filter
       db
       (filter (partial might-contain? old-bloom-filter))
       expression))))
