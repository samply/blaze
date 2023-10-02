(ns blaze.db.node.patient-last-change-index
  (:require
    [blaze.anomaly :refer [when-ok]]
    [blaze.db.impl.db :as db]
    [blaze.db.impl.index.patient-last-change :as plc]
    [blaze.db.node.tx-indexer :as tx-indexer]))


(defn index-entries
  {:arglists '([node tx-data])}
  [{:keys [search-param-registry] :as node} {:keys [t] :as tx-data}]
  (when-ok [entries (tx-indexer/index-tx search-param-registry (db/db node (dec t)) tx-data)]
    (-> (filterv (comp #{:patient-last-change-index} first) entries)
        (conj (plc/state-index-entry {:type :building :t t})))))
