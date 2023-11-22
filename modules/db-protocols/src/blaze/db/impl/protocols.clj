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

  (-instance-history [db tid id start-t since])

  (-total-num-of-instance-changes [_ tid id since])

  (-type-history [db type start-t start-id since])

  (-total-num-of-type-changes [db type since])

  (-system-history [db start-t start-tid start-id since])

  (-total-num-of-system-changes [db since])

  (-include [db resource-handle code] [db resource-handle code target-type])

  (-rev-include [db resource-handle] [db resource-handle source-type code])

  (-new-batch-db [db]))

(defprotocol Tx
  (-tx [tx t]))

(defprotocol QueryCompiler
  (-compile-type-query [compiler type clauses])

  (-compile-type-query-lenient [compiler type clauses])

  (-compile-system-query [compiler clauses])

  (-compile-compartment-query [compiler code type clauses])

  (-compile-compartment-query-lenient [compiler code type clauses]))

(defprotocol Query
  (-count [query context]
    "Returns a CompletableFuture that will complete with the count of the
    matching resource handles.")

  (-execute [query context] [query context arg1])

  (-clauses [query]))

(defprotocol Pull
  (-pull [pull resource-handle])

  (-pull-content [pull resource-handle])

  (-pull-many [pull resource-handles] [pull resource-handles elements]))

(defprotocol SearchParam
  (-compile-value [search-param modifier value] "Can return an anomaly.")
  (-resource-handles
    [search-param context tid modifier compiled-value]
    [search-param context tid modifier compiled-value start-id]
    "Returns a reducible collection.")
  (-sorted-resource-handles
    [search-param context tid direction]
    [search-param context tid direction start-id]
    "Returns a reducible collection.")
  (-count-resource-handles
    [search-param context tid modifier compiled-value]
    "Returns a CompletableFuture that will complete with the count of the
    matching resource handles.")
  (-compartment-keys [search-param context compartment tid compiled-value])
  (-matches? [search-param context resource-handle modifier compiled-values])
  (-compartment-ids [_ resolver resource])
  (-index-values [_ resolver resource])
  (-index-value-compiler [_]))

(defprotocol SearchParamRegistry
  (-get [_ code type])
  (-list-by-type [_ type])
  (-list-by-target [_ target])
  (-linked-compartments [_ resource])
  (-compartment-resources [_ compartment-type] [_ compartment-type type]))
