(ns blaze.db.impl.protocols)

(defprotocol Db
  (-node [db])

  (-t [db])

  (-since [db since])

  (-since-t [db])

  (-resource-handle [db tid id])

  (-type-list [db tid] [db tid start-id])

  (-type-total [db tid])

  (-system-list [_] [_ start-tid start-id])

  (-system-total [db])

  (-patient-compartment-last-change-t [db patient-id])

  (-patient-compartment-last-change-t-xf [db f])

  (-count-query [db query]
    "Returns a CompletableFuture that will complete with the count of the
    matching resource handles.")

  (-execute-query [db query] [db query arg1] [db query arg1 arg2])

  (-explain-query [db query])

  (-matcher-transducer [db matcher])

  (-instance-history [db tid id start-t])

  (-total-num-of-instance-changes [db tid id])

  (-type-history [db type start-t start-id])

  (-total-num-of-type-changes [db type])

  (-system-history [db start-t start-tid start-id])

  (-total-num-of-system-changes [db])

  (-changes [db])

  (-include [db resource-handle code] [db resource-handle code target-type])

  (-rev-include [db resource-handle] [db resource-handle source-type code])

  (-patient-everything [db patient-handle start end])

  (-re-index-total [db search-param-url])

  (-re-index [db search-param-url] [db search-param-url start-type start-id]))

(defprotocol Tx
  (-tx [tx t]))

(defprotocol QueryCompiler
  (-compile-type-query [compiler type clauses])

  (-compile-type-query-lenient [compiler type clauses])

  (-compile-type-matcher [compiler type clauses])

  (-compile-system-query [compiler clauses])

  (-compile-system-query-lenient [compiler clauses])

  (-compile-system-matcher [compiler clauses])

  (-compile-compartment-query [compiler code type] [compiler code type clauses])

  (-compile-compartment-query-lenient [compiler code type clauses]))

(defprotocol Query
  (-count [query db]
    "Returns a CompletableFuture that will complete with the count of the
    matching resource handles.")

  (-execute [query db] [query db arg1] [query db arg1 arg2])

  (-query-clauses [query])

  (-query-plan [query db])

  (-query-match-extension-fn [query]
    "Returns a function of a resource matched by `query` that returns its match
    extensions or nil if there are none. Returns nil if no clause of `query`
    produces match extensions."))

(defprotocol Matcher
  (-transducer [matcher db])

  (-matcher-clauses [matcher]))

(defprotocol Pull
  (-pull [pull resource-handle variant])

  (-pull-content [pull resource-handle variant])

  (-pull-many [pull resource-handles opts])

  (-pull-fn [pull opts]))

(defprotocol SearchParam
  (-validate-modifier [search-param modifier] "Can return an anomaly.")
  (-compile-value [search-param modifier value]
    "Returns a CompletableFuture that completes with the compiled value or
    completes exceptionally with an anomaly.")
  (-estimated-scan-size
    [search-param db tid modifier compiled-value]
    "Returns a relative estimation of the amount of work to do while scanning
    the index of `search-param` with `compiled-value` under `tid`.

    The metric is relative and unitless. It can be only used to compare the
    amount of scan work between different search params.

    Returns an anomaly on errors.")
  (-index-handles
    [search-param db tid modifier compiled-value]
    [search-param db tid modifier compiled-value start-id]
    "Returns a reducible collection of unordered index handles.")
  (-supports-ordered-index-handles
    [search-param db tid modifier compiled-values]
    "Returns true if `search-param` supports ordered index handles.")
  (-ordered-index-handles
    [search-param db tid modifier compiled-values]
    [search-param db tid modifier compiled-values start-id]
    "Returns a reducible collection of index handles ordered by ID.")
  (-sorted-index-handles
    [search-param db tid direction]
    [search-param db tid direction start-id]
    "Returns a reducible collection of index handles sorted by the sort clause.

    The `start-id` is only a performance hint. Search params are allowed to
    return all index handles, even if a `start-id` is supplied.")
  (-supports-ordered-compartment-index-handles [search-param modifier values]
    "Returns true if `search-param` supports fetching ordered compartment index handles with `values`.")
  (-ordered-compartment-index-handles
    [search-param db compartment tid modifier compiled-values]
    [search-param db compartment tid modifier compiled-values start-id]
    "Returns a reducible collection.")
  (-matcher [_ db modifier compiled-values])
  (-single-version-id-matcher [_ db tid modifier compiled-values])
  (-postprocess-matches
    [search-param db modifier values compiled-values]
    "Returns a transducer that will be applied on every matching resource-handle
     at the end of the query execution. Can also be used to finally remove
     unwanted matches.")
  (-compartment-ids [_ resolver resource])
  (-index-values [_ resolver resource])
  (-index-value-compiler [_]))

(defprotocol MatchExtension
  (-match-extension-fn [search-param modifier compiled-values]
    "Returns a function of a matching resource that returns the extension that
    will be added to its search entry or nil if there is none. Returns nil if
    `search-param` never produces match extensions."))

(extend-protocol MatchExtension
  Object
  (-match-extension-fn [_ _ _]))

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
