(ns blaze.middleware.fhir.db
  "This middleware provides a database value for read-only interactions.

  It uses the optional query param __t and vid from path to acquire the right
  database value."
  (:refer-clojure :exclude [sync])
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.handler.fhir.util :as fhir-util]
   [cognitect.anomalies :as anom])
  (:import
   [java.util.concurrent TimeUnit]))

(defn- timeout-msg [timeout]
  (format "Timeout while trying to acquire the latest known database state. At least one known transaction hasn't been completed yet. Please try to lower the transaction load or increase the timeout of %d ms by setting DB_SYNC_TIMEOUT to a higher value if you see this often." timeout))

(defn- timeout-t-msg [t timeout]
  (format "Timeout while trying to acquire the database state with t=%d. The indexer has probably fallen behind. Please try to lower the transaction load or increase the timeout of %d ms by setting DB_SYNC_TIMEOUT to a higher value if you see this often." t timeout))

(defn- sync [node timeout]
  (-> (d/sync node)
      (ac/or-timeout! timeout TimeUnit/MILLISECONDS)
      (ac/exceptionally #(cond-> % (ba/busy? %) (assoc ::anom/message (timeout-msg timeout))))))

(defn wrap-db
  "Default database wrapping.

  Always syncs on the latest known database state."
  [handler node timeout]
  (fn [request]
    (if (:blaze/db request)
      (handler request)
      (-> (sync node timeout)
          (ac/then-compose #(handler (assoc request :blaze/db %)))))))

(defn- sync-t [node t timeout]
  (-> (do-sync [db (d/sync node t)]
        (d/as-of db t))
      (ac/or-timeout! timeout TimeUnit/MILLISECONDS)
      (ac/exceptionally #(cond-> % (ba/busy? %) (assoc ::anom/message (timeout-t-msg t timeout))))))

(defn wrap-search-db
  "Database wrapping for requests that like to either operate on the latest
  known database state or a known database state.

  Currently the `t` of the database state taken from the query or form param
  `__t`."
  [handler node timeout]
  (fn [{:keys [params] :as request}]
    (if (:blaze/db request)
      (handler request)
      (-> (if-let [t (fhir-util/t params)]
            (sync-t node t timeout)
            (sync node timeout))
          (ac/then-compose #(handler (assoc request :blaze/db %)))))))

(defn wrap-snapshot-db
  "Database wrapping for requests that like to operate on a known database state.

  Currently the `t` of the database state taken from the query or form param
  `__t`."
  [handler node timeout]
  (fn [{:keys [params] :as request}]
    (if (:blaze/db request)
      (handler request)
      (if-let [t (fhir-util/t params)]
        (-> (sync-t node t timeout)
            (ac/then-compose #(handler (assoc request :blaze/db %))))
        (ac/completed-future
         (ba/incorrect (format "Missing or invalid `__t` query param `%s`." (params "__t"))))))))

(defn wrap-versioned-instance-db
  "Database wrapping for versioned read requests.

  The `t` of the database state is taken from the path param `vid`."
  [handler node timeout]
  (fn [{{:keys [vid]} :path-params :as request}]
    (if (:blaze/db request)
      (handler request)
      (if-let [t (some-> vid fhir-util/parse-nat-long)]
        (-> (sync-t node t timeout)
            (ac/then-compose #(handler (assoc request :blaze/db %))))
        (ac/completed-future
         (ba/incorrect
          (format "Resource versionId `%s` is invalid." vid)
          :fhir/issue "value"
          :fhir/operation-outcome "MSG_ID_INVALID"))))))
