(ns blaze.db.impl.protocols)

(defprotocol Db
  (-node [db])

  (-as-of [db t])

  (-basis-t [db])

  (-as-of-t [db])

  (-resource-handle [db tid id])

  (-type-list [db tid] [db tid start-id])

  (-type-total [db tid])

  (-system-list [_] [_ start-tid start-id])

  (-system-total [db])

  (-compartment-resource-handles
    [db compartment tid]
    [db compartment tid start-id])

  (-patient-compartment-last-change-t [db patient-id])

  (-count-query [db query]
    "Returns a CompletableFuture that will complete with the count of the
    matching resource handles.")

  (-execute-query [db query] [db query arg1])

  (-matcher-transducer [db matcher])

  (-stop-history-at [db instant])

  (-instance-history [db tid id start-t])

  (-total-num-of-instance-changes [_ tid id since])

  (-type-history [db type start-t start-id])

  (-total-num-of-type-changes [db type since])

  (-system-history [db start-t start-tid start-id])

  (-total-num-of-system-changes [db since])

  (-changes [db])

  (-include [db resource-handle code] [db resource-handle code target-type])

  (-rev-include [db resource-handle] [db resource-handle source-type code])

  (-patient-everything [db patient-handle start end])

  (-re-index-total [db search-param-url])

  (-re-index [db search-param-url] [db search-param-url start-type start-id])

  (-new-batch-db [db]))

(defprotocol Tx
  (-tx [tx t]))

(defprotocol QueryCompiler
  (-compile-type-query [compiler type clauses])

  (-compile-type-query-lenient [compiler type clauses])

  (-compile-type-matcher [compiler type clauses])

  (-compile-system-query [compiler clauses])

  (-compile-system-matcher [compiler clauses])

  (-compile-compartment-query [compiler code type clauses])

  (-compile-compartment-query-lenient [compiler code type clauses]))

(defprotocol Query
  (-count [query batch-db]
    "Returns a CompletableFuture that will complete with the count of the
    matching resource handles.")

  (-execute [query batch-db] [query batch-db arg1])

  (-query-clauses [query]))

(defprotocol Matcher
  (-transducer [matcher batch-db])

  (-matcher-clauses [matcher]))

(defprotocol Pull
  (-pull [pull resource-handle variant])

  (-pull-content [pull resource-handle variant])

  (-pull-many [pull resource-handles variant]))

(defprotocol SearchParam
  (-compile-value [search-param modifier value] "Can return an anomaly.")
  (-resource-handles
    [search-param batch-db tid modifier compiled-value]
    [search-param batch-db tid modifier compiled-value start-id]
    "Returns a reducible collection.")
  (-sorted-resource-handles
    [search-param batch-db tid direction]
    [search-param batch-db tid direction start-id]
    "Returns a reducible collection.")
  (-chunked-resource-handles
    [search-param batch-db tid modifier compiled-value])
  (-compartment-keys [search-param context compartment tid compiled-value])
  (-matcher [_ batch-db modifier values])
  (-compartment-ids [_ resolver resource])
  (-index-values [_ resolver resource])
  (-index-value-compiler [_]))

(defprotocol SearchParamRegistry
  (-parse [_ type s])
  (-get [_ code type])
  (-get-by-url [_ url])
  (-all-types [_])
  (-list-by-type [_ type])
  (-list-by-target [_ target])
  (-linked-compartments [_ resource])
  (-compartment-resources [_ compartment-type] [_ compartment-type type])
  (-patient-compartment-search-param-codes [_ type]))
