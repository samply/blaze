(ns blaze.db.node.validation
  (:require
   [blaze.anomaly :as ba]
   [blaze.db.node.duplicates :as dup]))

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
