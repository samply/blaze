(ns blaze.interaction.search.params
  (:require
    [blaze.anomaly :as ba :refer [when-ok]]
    [blaze.anomaly-spec]
    [blaze.async.comp :as ac :refer [do-sync]]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.interaction.search.params.include :as include]
    [blaze.interaction.util :as iu]
    [blaze.page-store :as page-store]
    [blaze.page-store.spec]
    [clojure.spec.alpha :as s]))


(defn- clauses [page-store {token "__token" :as query-params}]
  (cond
    (s/valid? ::page-store/token token)
    (-> (do-sync [clauses (page-store/get page-store token)]
          {:clauses clauses
           :token token})
        (ac/exceptionally #(assoc % :http/status 422)))

    token
    (ac/completed-future
      (ba/incorrect
        (format "Invalid token `%s`." token)
        :http/status 422))

    :else
    (ac/completed-future {:clauses (iu/clauses query-params)})))


(defn- summary?
  "Returns true if a summary result is requested."
  [{summary "_summary" :as query-params}]
  (or (zero? (fhir-util/page-size query-params)) (= "count" summary)))


(defn decode
  "Returns a CompletableFuture that will complete with decoded params or
  complete exceptionally in case of errors."
  [page-store handling query-params]
  (do-sync [{:keys [clauses token]} (clauses page-store query-params)]
    (when-ok [include-defs (include/include-defs handling query-params)]
      (cond->
        {:clauses clauses
         :include-defs include-defs
         :summary? (summary? query-params)
         :summary (get query-params "_summary")
         :page-size (fhir-util/page-size query-params)
         :page-type (fhir-util/page-type query-params)
         :page-id (fhir-util/page-id query-params)
         :page-offset (fhir-util/page-offset query-params)}
        token
        (assoc :token token)))))
