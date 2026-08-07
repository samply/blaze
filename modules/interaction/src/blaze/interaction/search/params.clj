(ns blaze.interaction.search.params
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.interaction.search.params.include :as include]
   [blaze.page-store :as page-store]
   [blaze.page-store.spec]
   [blaze.util :as u :refer [str]]
   [blaze.util.clauses :as uc]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]))

(defn- clauses [page-store {token "__token" :as query-params}]
  (cond
    (s/valid? ::page-store/token token)
    (do-sync [clauses (page-store/get page-store token)]
      {:clauses clauses
       :token token})

    token
    (ac/completed-future
     (ba/incorrect
      (format "Invalid token `%s`." token)
      :http/status 422))

    :else
    (ac/completed-future
     (when-ok [clauses (uc/clauses query-params)]
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

  Decoded params consist of:
   :clauses - query clauses
   :token - possibly a token encoding the query clauses"
  [page-store handling query-params]
  (do-sync [{:keys [clauses token]} (clauses page-store query-params)]
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
          :page-type (fhir-util/page-type query-params)
          :page-id (fhir-util/page-id query-params)
          :page-id-stack (fhir-util/page-id-stack query-params)
          :page-offset (fhir-util/page-offset query-params)}
          token (assoc :token token)
          total (assoc :total total))))))
