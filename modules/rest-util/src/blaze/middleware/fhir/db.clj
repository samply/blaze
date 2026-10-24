(ns blaze.middleware.fhir.db
  "This middleware provides a database value for read-only interactions.

  It uses the optional paging param __t and vid from path to acquire the right
  database value."
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.handler.fhir.util :as fhir-util]))

(defn wrap-db
  "Default database wrapping.

  Always syncs on the latest known database state. The database value is valid
  until the future returned by `handler` completes."
  [handler node]
  (fn [request]
    (if (:blaze/db request)
      (handler request)
      (d/with-sync-db node #(handler (assoc request :blaze/db %))))))

(defn wrap-snapshot-db
  "Database wrapping for requests that like to operate on a known database state.

  The logical timestamp `t` of the database state is taken from the paging
  param `__t` which is only available from decrypted page ids. The database
  value is valid until the future returned by `handler` completes."
  [handler node]
  (fn [{:blaze/keys [paging-params] :as request}]
    (if (:blaze/db request)
      (handler request)
      (if-let [t (fhir-util/t paging-params)]
        (d/with-sync-db node t #(handler (assoc request :blaze/db %)))
        (ac/completed-future
         (ba/incorrect (format "Missing or invalid `__t` paging param `%s`." (get paging-params "__t"))))))))
