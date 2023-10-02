(ns blaze.elm.expression.cache.bloom-filter
  (:require
   [blaze.db.api :as d]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.external-data :as ed]
   [blaze.elm.expression :as expr]
   [blaze.elm.expression.cache.codec :as codec]
   [prometheus.alpha :as prom :refer [defhistogram]]
   [taoensso.timbre :as log])
  (:import
   [blaze.elm.compiler.external_data Resource]
   [blaze.elm.expression.cache.codec BloomFilterContainer]
   [com.google.common.hash BloomFilter]
   [java.time OffsetDateTime]))

(set! *warn-on-reflection* true)

(defhistogram bloom-filter-bytes
  "Bloom filter sizes in bytes."
  {:namespace "blaze"
   :subsystem "cql_expr_cache"}
  (take 12 (iterate #(* 4 %) 1)))

(defhistogram bloom-filter-creation-duration-seconds
  "Durations in Cassandra resource store."
  {:namespace "blaze"
   :subsystem "cql_expr_cache"}
  (take 14 (iterate #(* 2 %) 0.1)))

(defn might-contain?
  "Returns true if `resource` might have been put in `bloom-filter` or false if
  this is definitely not the case."
  {:arglists '([bloom-filter resource])}
  [^BloomFilterContainer bloom-filter ^Resource resource]
  (or (< (.-t bloom-filter) (.-lastChangeT resource))
      (.mightContain ^BloomFilter (.-filter bloom-filter) (.-id resource))))

(defn- calc-mem-size [n p]
  (long (/ (* (- n) (Math/log p)) (* (Math/log 2) (Math/log 2)) 8)))

(defn- build-bloom-filter [expression t resource-ids]
  (let [n (count resource-ids)
        p (double 0.01)
        filter (BloomFilter/create codec/id-funnel (int n) p)
        mem-size (calc-mem-size n p)]
    (prom/observe! bloom-filter-bytes mem-size)
    (run! #(.put filter %) resource-ids)
    (BloomFilterContainer. t (pr-str (core/-form expression)) n filter mem-size)))

(defn- calc-bloom-filter [db xform expression]
  (with-open [batch-db (d/new-batch-db db)
              _ (prom/timer bloom-filter-creation-duration-seconds)]
    (build-bloom-filter
     expression
     (d/t batch-db)
     (into
      []
      (comp (map (partial ed/mk-resource batch-db))
            xform
            (filter (partial expr/eval {:db batch-db :now (OffsetDateTime/now)} expression))
            (map :id))
      (d/type-list db "Patient")))))

(defn create [node expression]
  (let [db (d/db node)]
    (log/debug "Create Bloom filter for expression"
               (core/-form expression)
               "evaluating it for"
               (d/type-total db "Patient")
               "Patient resources")
    (calc-bloom-filter db (map identity) expression)))

(defn recreate
  {:arglists '([node old-bloom-filter expression])}
  [node {::keys [t] :as old-bloom-filter} expression]
  (let [db (d/db node)]
    (log/debug "Recreate Bloom filter for expression"
               (core/-form expression)
               "last created at t =" t
               "evaluating it for"
               (d/type-total db "Patient")
               "Patient resources")
    (calc-bloom-filter
     db
     (filter (partial might-contain? old-bloom-filter))
     expression)))
