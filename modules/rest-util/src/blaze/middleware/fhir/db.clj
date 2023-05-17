(ns blaze.middleware.fhir.db
  "This middleware provides a database value for read-only interactions.

  It uses the optional query param __t and vid from path to acquire the right
  database value."
  (:require
    [blaze.async.comp :as ac :refer [do-sync]]
    [blaze.db.api :as d]
    [blaze.handler.fhir.util :as fhir-util]
    [cognitect.anomalies :as anom])
  (:import
    [java.util.concurrent TimeUnit]))


(defn- vid [{{:keys [vid]} :path-params}]
  (some-> vid fhir-util/parse-nat-long))


(defn- timeout-msg [timeout]
  (format "Timeout while trying to acquire the latest known database state. At least one known transaction hasn't been completed yet. Please try to lower the transaction load or increase the timeout of %d ms by setting DB_SYNC_TIMEOUT to a higher value if you see this often.", timeout))


(defn- db [node timeout {:keys [params] :as request}]
  (if-let [t (vid request)]
    (do-sync [db (d/sync node t)]
      (d/as-of db t))
    (if-let [t (fhir-util/t params)]
      (do-sync [db (d/sync node t)]
        (d/as-of db t))
      (-> (d/sync node)
          (ac/or-timeout! timeout TimeUnit/MILLISECONDS)
          (ac/exceptionally #(assoc % ::anom/message (timeout-msg timeout)))))))


(defn wrap-db
  ([handler node]
   (wrap-db handler node 10000))
  ([handler node timeout]
   (fn [request]
     (if (:blaze/db request)
       (handler request)
       (-> (db node timeout request)
           (ac/then-compose #(handler (assoc request :blaze/db %))))))))
