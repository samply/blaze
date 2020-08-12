(ns blaze.db.impl.protocols
  (:import
    [clojure.lang IReduceInit]))


(defprotocol Node
  (-db [node])

  (-sync [node t])

  (-submit-tx [node tx-ops])

  (-tx-result [node t]))


(defprotocol Db
  (-node [db])

  (-as-of [db t])

  (-basis-t [db])

  (-as-of-t [db])

  (-resource-handle [db type id])

  (-list-resource-handles ^IReduceInit [db type start-id])

  (-type-total [db type])

  (-system-list ^IReduceInit [_ start-type start-id])

  (-system-total [db])

  (-list-compartment-resource-handles
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


(defprotocol Tx
  (-tx [tx t]))


(defprotocol QueryCompiler
  (-compile-type-query [compiler type clauses])

  (-compile-system-query [compiler clauses])

  (-compile-compartment-query [compiler code type clauses]))


(defprotocol Query
  (-execute
    [query node snapshot raoi svri rsvi csvri t]
    [query node snapshot raoi svri rsvi csvri t arg1]))


(defprotocol SearchParam
  (-compile-values [search-param values])
  (-resource-handles [search-param snapshot spvi rsvi raoi tid modifier compiled-value t])
  (-compartment-keys [search-param cspvi compartment tid compiled-value])
  (-matches? [search-param snapshot tid id hash modifier compiled-values])
  (-compartment-ids [_ resolver resource])
  (-index-entries [_ resolver hash resource linked-compartments]))


(defprotocol Pull
  (-pull [pull resource-handle])

  (-pull-content [pull resource-handle]))
