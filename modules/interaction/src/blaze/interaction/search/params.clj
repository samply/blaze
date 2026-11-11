(ns blaze.interaction.search.params
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.fhir.search-clauses :as fsc]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.interaction.search.params.include :as include]
   [blaze.page-store :as page-store]
   [blaze.page-store.spec]
   [blaze.util :as u :refer [str]]
   [blaze.value-set-expander :as vse]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]))

(defn- token-params
  "Returns the params of a page id with `token` holding the `clauses` from the
  page store.

  If the clauses hold expanded value sets, the `query-params` of the page id
  hold the clauses as the client wrote them, which are returned under
  :client-clauses."
  [query-params token clauses]
  (when-ok [client-clauses (fsc/clauses query-params)]
    (cond-> {:clauses clauses
             :token token}
      (seq client-clauses)
      (assoc :client-clauses client-clauses))))

(defn- clauses [page-store query-params {token "__token"}]
  (cond
    (s/valid? ::page-store/token token)
    (do-sync [clauses (page-store/get page-store token)]
      (token-params query-params token clauses))

    token
    (ac/completed-future
     (ba/incorrect
      (format "Invalid token `%s`." token)
      :http/status 422))

    :else
    (ac/completed-future
     (when-ok [clauses (fsc/clauses query-params)]
       {:clauses clauses}))))

(def ^:private summary-mode-values
  "The `_summary` values that select a summary mode."
  #{"true" "count"})

(def ^:private supported-summary-values
  "The `_summary` values Blaze honors.

  Besides the modes, this includes `false`, which asks for the complete
  resource. That's what Blaze returns anyway if no mode is selected, so `false`
  is honored without selecting one."
  (conj summary-mode-values "false"))

(defn- summary-mode
  "Returns the summary mode requested by the `_summary` query param or nil if
  none is requested.

  Returns nil for `false` as well, because it asks for the complete resource,
  which is what a missing summary mode already means.

  Returns an unsupported anomaly if `handling` is strict and none of the given
  values is supported."
  [handling {summary "_summary"}]
  (let [values (u/to-seq summary)]
    (if (and (identical? :blaze.preference.handling/strict handling)
             (seq values)
             (not-any? supported-summary-values values))
      (ba/unsupported (str "Unsupported _summary search param with value(s): " (str/join ", " values)))
      (some summary-mode-values values))))

(defn- count?
  "Returns true if a summary=count result is requested."
  [summary query-params]
  (or (zero? (fhir-util/page-size query-params)) (= "count" summary)))

(defn- explain?
  "Returns true if a query plan is requested."
  [{explain "__explain"}]
  (= "true" explain))

(defn- total [{total "_total"}]
  (when (= "accurate" total)
    total))

(defn decode
  "Returns a CompletableFuture that will complete with decoded params or
  complete exceptionally in case of errors.

  The paging state like the token and the start of the page is only taken from
  `paging-params` which originate from a decrypted page id and never from
  `query-params`.

  Decoded params consist of:
   :clauses - query clauses
   :token - possibly a token encoding the query clauses
   :client-clauses - the clauses as the client wrote them, if they differ
                     from the query clauses held by the token, because value
                     sets were expanded"
  [page-store handling query-params paging-params]
  (do-sync [{:keys [clauses token client-clauses]} (clauses page-store query-params paging-params)]
    (when-ok [include-defs (include/include-defs handling query-params)
              summary (summary-mode handling query-params)]
      (let [total (total query-params)]
        (cond->
         {:clauses clauses
          :include-defs include-defs
          :summary? (count? summary query-params)
          :summary summary
          :elements (fhir-util/elements query-params)
          :explain? (explain? query-params)
          :page-size (fhir-util/page-size query-params)
          :page-type (fhir-util/page-type paging-params)
          :page-id (fhir-util/page-id paging-params)
          :page-id-stack (fhir-util/page-id-stack paging-params)
          :page-offset (fhir-util/page-offset paging-params)}
          token (assoc :token token)
          client-clauses (assoc :client-clauses client-clauses)
          total (assoc :total total))))))

(defn expand-value-sets
  "Returns a CompletableFuture that will complete with `params` whose :clauses
  have their value sets expanded (see `vse/expand-clauses`), or will complete
  exceptionally if a value set can't be expanded.

  If the clauses can't match any resource, because a value set is empty, keeps
  them and marks `params` with :unsatisfiable? true, so that the search doesn't
  need to query the database."
  {:arglists '([value-set-expander type params])}
  [value-set-expander type {:keys [clauses] :as params}]
  (if (seq clauses)
    (do-sync [expanded-clauses (vse/expand-clauses value-set-expander type clauses)]
      (if expanded-clauses
        (assoc params :clauses expanded-clauses)
        (assoc params :unsatisfiable? true)))
    (ac/completed-future params)))
