(ns blaze.db.node.validation
  (:require
   [blaze.anomaly :as ba]
   [blaze.db.node.duplicates :as dup]
   [blaze.db.node.tx-indexer.util :as tx-u]
   [cognitect.anomalies :as anom]))

(defn validate-tx-cmds
  "Validates `tx-cmds` for commands acting on the same resource.

  Returns an anomaly if two commands conflict. It carries the positions of both
  commands in `tx-cmds` under :blaze.db/tx-cmd-indices, the earlier one first."
  [tx-cmds]
  (when-let [{:keys [cmd idx existing-cmd existing-idx]}
             (dup/find-duplicate tx-cmds)]
    (ba/incorrect
     (dup/duplicate-msg existing-cmd cmd)
     :fhir/issue "invariant"
     :blaze.db/tx-cmd-indices [existing-idx idx])))

(defn- tx-cmd-clauses [{:keys [op type if-none-exist clauses]}]
  (case op
    "create" (when if-none-exist [(tx-u/conditional-create-msg type if-none-exist) if-none-exist])
    "conditional-delete" (when clauses [(tx-u/conditional-delete-msg type clauses) clauses])
    nil))

(defn- unexpanded-value-set-anom
  [search-param-registry {:keys [type] :as tx-cmd}]
  (when-let [[op-msg clauses] (tx-cmd-clauses tx-cmd)]
    (when-let [url (tx-u/value-set-url search-param-registry type clauses)]
      (update (tx-u/unexpanded-value-set-anom op-msg url) ::anom/message str
              " Value sets have to be expanded into plain token clauses before transacting."))))

(defn validate-value-sets
  "Validates that no clause of a conditional create or delete in `tx-cmds` has
  a value set search param.

  The database doesn't expand value sets, because the outcome of an expansion
  can change over time and a replay of the transaction log has to give the
  same outcome. So value sets have to be expanded before transacting.

  Returns an anomaly for the first such clause found."
  [search-param-registry tx-cmds]
  (some (partial unexpanded-value-set-anom search-param-registry) tx-cmds))
