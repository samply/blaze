(ns blaze.elm.expression.cache.bloom-filter
  (:refer-clojure :exclude [merge])
  (:require
   [blaze.db.api :as d]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.expression :as expr]
   [blaze.elm.expression.cache.codec :as codec]
   [blaze.elm.expression.cache.codec.form :as form]
   [blaze.elm.resource :as cr]
   [java-time.api :as time]
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
  this is definitely not the case."
  {:arglists '([bloom-filter resource])}
  [^BloomFilterContainer bloom-filter ^Resource resource]
  (or (< (.-t bloom-filter) (.-lastChangeT resource))
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
        expr-form (pr-str (core/-form expression))]
    (prom/observe! bloom-filter-bytes mem-size)
    (run! #(.put filter %) resource-ids)
    (BloomFilterContainer. (form/hash expr-form) t expr-form n filter mem-size)))

(defn- calc-bloom-filter [db xform expression]
  (with-open [batch-db (d/new-batch-db db)
              _ (prom/timer bloom-filter-creation-duration-seconds)]
    (build-bloom-filter
     expression
     (d/t batch-db)
     (into
      []
      (comp (map (partial cr/mk-resource batch-db))
            xform
            (filter (partial expr/eval {:db batch-db :now (time/offset-date-time)} expression))
            (map :id))
      (d/type-list db "Patient")))))

(defn- create-bloom-filter-msg [expression db]
  (format "Create Bloom filter for expression `%s` evaluating it for %d patients."
          (core/-form expression) (d/type-total db "Patient")))

(defn create [node expression]
  (let [db (d/db node)]
    (log/debug (create-bloom-filter-msg expression db))
    (calc-bloom-filter db identity expression)))

(defn- recreate-bloom-filter-msg [expr-form t db]
  (format "Recreate Bloom filter for expression `%s` last created at t = %d evaluating it for %d patients."
          expr-form t (d/type-total db "Patient")))

(defn recreate
  {:arglists '([node old-bloom-filter expression])}
  [node {::keys [t expr-form] :as old-bloom-filter} expression]
  (let [db (d/db node)]
    (log/debug (recreate-bloom-filter-msg expr-form t db))
    (calc-bloom-filter
     db
     (filter (partial might-contain? old-bloom-filter))
     expression)))
