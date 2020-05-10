(ns blaze.db.impl.protocols)


(defprotocol Node
  (-db [node])

  (-sync [node t])

  (-submit-tx [node tx-ops]))


(defprotocol Db
  (-as-of [db t])

  (-basis-t [db])

  (-as-of-t [db])

  (-tx [db t])

  (-resource-exists? [db type id])

  (-resource [db type id])

  (-list-resources [db type] [db type start-id])

  (-list-compartment-resources [db code id type] [db code id type start-id])

  (-execute-query
    ^clojure.lang.IReduceInit [db query]
    ^clojure.lang.IReduceInit [db query arg1])

  (-instance-history [db type id start-t since])

  (-total-num-of-instance-changes [_ type id since])

  (-type-history [db type start-t start-id since])

  (-type-total [db type])

  (-total-num-of-type-changes [db type since])

  (-system-history [db start-t start-type start-id since])

  (-system-total [db])

  (-total-num-of-system-changes [db since])

  (-new-batch-db [db]))


(defprotocol QueryCompiler
  (-compile-type-query [compiler type clauses])

  (-compile-compartment-query [compiler code type clauses]))


(defprotocol Query
  (-execute
    [query context snapshot raoi cspvi t]
    [query context snapshot raoi cspvi t arg1]))
