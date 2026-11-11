(ns blaze.db.node.tx-indexer.util
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba]
   [blaze.db.impl.codec :as codec]
   [blaze.db.search-param-registry :as sr]
   [blaze.util :refer [str]]
   [clojure.string :as str]
   [prometheus.alpha :refer [defhistogram]]))

(defhistogram duration-seconds
  "Durations in transaction indexer.

  The `node` label distinguishes the individual nodes like main and admin.

  The buckets are the same as those of the node durations, so that the
  durations of both can be compared."
  {:namespace "blaze"
   :subsystem "db_tx_indexer"}
  (take 23 (iterate #(* 2 %) 1e-6))
  "node" "op")

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

(defn conditional-create-msg [type clauses]
  (format "Conditional create of a %s with query `%s`" type
          (clauses->query-params clauses)))

(defn conditional-delete-msg [type clauses]
  (format "Conditional delete of %ss with query `%s`" type
          (clauses->query-params clauses)))

(defn value-set-url
  "Returns the URL of the first value set in the search `clauses` of resources
  of `type` or nil if there is none (see `sr/value-set-param-code`)."
  [search-param-registry type clauses]
  (some
   (fn [[param url]]
     (when (sr/value-set-param-code search-param-registry type param)
       url))
   clauses))

(defn unexpanded-value-set-anom
  "Returns a fault saying that the operation described by `op-msg` contains
  the unexpanded value set with `url`.

  Carries `url` under :blaze.db/unexpanded-value-set, so that the indexer can
  tell such an operation from others failing to evaluate their query."
  [op-msg url]
  (ba/fault
   (format "%s contains the unexpanded value set `%s`." op-msg url)
   :blaze.db/unexpanded-value-set url))
