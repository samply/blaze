(ns blaze.db.node.tx-indexer
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.node.tx-indexer.expand :as expand]
   [blaze.db.node.tx-indexer.util :as tx-u]
   [blaze.db.node.tx-indexer.verify :as verify]
   [prometheus.alpha :as prom]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(defn index-tx
  {:arglists '([context tx-data])}
  [{:keys [db-before] :as context} {:keys [t tx-cmds]}]
  (log/trace "verify transaction commands with t =" t
             "based on db with t =" (d/basis-t db-before))
  (let [db-before (d/new-batch-db db-before)]
    (-> (expand/expand-tx-cmds db-before tx-cmds)
        (ac/then-apply
         (fn [tx-cmds]
           (with-open [_ (prom/timer tx-u/duration-seconds "verify-tx-cmds")]
             (verify/verify-tx-cmds (assoc context :db-before db-before) t tx-cmds))))
        (ac/when-complete (fn [_ _] (.close db-before))))))
