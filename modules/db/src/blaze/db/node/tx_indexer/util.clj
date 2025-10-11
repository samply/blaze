(ns blaze.db.node.tx-indexer.util
  (:refer-clojure :exclude [str])
  (:require
   [blaze.util :refer [str]]
   [clojure.string :as str]
   [prometheus.alpha :refer [defhistogram]]))

(defhistogram duration-seconds
  "Durations in transaction indexer."
  {:namespace "blaze"
   :subsystem "db_tx_indexer"}
  (take 16 (iterate #(* 2 %) 0.0001))
  "op")

(defn clauses->query-params [clauses]
  (->> clauses
       (map (fn [[param & values]] (str param "=" (str/join "," values))))
       (str/join "&")))
