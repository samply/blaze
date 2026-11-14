(ns blaze.db.node.tx-indexer.util
  (:refer-clojure :exclude [str])
  (:require
   [blaze.db.impl.codec :as codec]
   [blaze.util :refer [str]]
   [clojure.string :as str]
   [prometheus.alpha :refer [defhistogram]]))

(defhistogram duration-seconds
  "Durations in transaction indexer."
  {:namespace "blaze"
   :subsystem "db_tx_indexer"}
  (take 16 (iterate #(* 2 %) 0.0001))
  "op")

(defn rev-include-param
  "Returns the tuple of `type`, its tid and the c-hash of `code` that stands
  for the reference search param with `code` of resources of `type` in the
  check of the references to a deleted resource.

  The expansion of a patient purge names the search params the check can
  leave out with such tuples, so that the check compares them with its own
  without computing tids or c-hashes."
  [type code]
  [type (codec/tid type) (codec/c-hash code)])

(defn clauses->query-params [clauses]
  (->> clauses
       (map (fn [[param & values]] (str param "=" (str/join "," values))))
       (str/join "&")))
