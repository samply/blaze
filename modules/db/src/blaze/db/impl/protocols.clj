(ns blaze.db.impl.protocols
  (:import
    [clojure.lang IReduceInit]))


(defprotocol Node
  (-db [node])

  (-sync [node t])

  (-submit-tx [node tx-ops]))


(defprotocol ResourceContentLookup
  "Resource content access by content-hash."

  (-get-content [rs hash]))


(defprotocol Db
  (-as-of [db t])

  (-basis-t [db])

  (-as-of-t [db])

  (-tx [db t])

  (-resource-exists? [db type id])

  (-resource [db type id])

  (-list-resources ^IReduceInit [db type start-id])

  (-type-total [db type])

  (-system-list ^IReduceInit [_ start-type start-id])

  (-system-total [db])

  (-list-compartment-resources
    ^IReduceInit [db code id type start-id])

  (-execute-query
    ^IReduceInit [db query]
    ^IReduceInit [db query arg1])

  (-instance-history ^IReduceInit [db type id start-t since])

  (-total-num-of-instance-changes [_ type id since])

  (-type-history ^IReduceInit [db type start-t start-id since])

  (-total-num-of-type-changes [db type since])

  (-system-history
    ^IReduceInit [db start-t start-type start-id since])

  (-total-num-of-system-changes [db since])

  (-new-batch-db [db]))


(defprotocol QueryCompiler
  (-compile-type-query [compiler type clauses])

  (-compile-system-query [compiler clauses])

  (-compile-compartment-query [compiler code type clauses]))


(defprotocol Query
  (-execute
    [query node snapshot raoi svri csvri t]
    [query node snapshot raoi svri csvri t arg1]))
