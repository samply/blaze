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

(def ^:private allowed-summary-values
  #{"true" "count"})

(defn- summary [handling {summary "_summary"}]
  (let [value (some allowed-summary-values (u/to-seq summary))]
    (if (and (nil? value)
             (identical? :blaze.preference.handling/strict handling)
             (seq (u/to-seq summary)))
      (ba/unsupported (str "Unsupported _summary search param with value(s): " (str/join ", " (u/to-seq summary))))
      value)))

(defn- summary?
  "Returns true if a summary result is requested."
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
              summary (summary handling query-params)]
      (let [total (total query-params)]
        (cond->
         {:clauses clauses
          :include-defs include-defs
          :summary? (summary? summary query-params)
          :summary summary
          :elements (fhir-util/elements query-params)
          :explain? (explain? query-params)
          :page-size (fhir-util/page-size query-params)
          :page-type (fhir-util/page-type query-params)
          :page-id (fhir-util/page-id query-params)
          :page-offset (fhir-util/page-offset query-params)}
          token (assoc :token token)
          total (assoc :total total))))))
