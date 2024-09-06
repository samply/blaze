(ns blaze.middleware.fhir.db
  "This middleware provides a database value for read-only interactions.

  It uses the optional query param __t and vid from path to acquire the right
  database value."
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.handler.fhir.util :as fhir-util]))

(defn wrap-db
  "Default database wrapping.

  Always syncs on the latest known database state."
  [handler node timeout]
  (fn [request]
    (if (:blaze/db request)
      (handler request)
      (-> (fhir-util/sync node timeout)
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
        (-> (fhir-util/sync node t timeout)
            (ac/then-compose #(handler (assoc request :blaze/db %))))
        (ac/completed-future
         (ba/incorrect (format "Missing or invalid `__t` query param `%s`." (get params "__t"))))))))

(defn wrap-versioned-instance-db
  "Database wrapping for versioned read requests.

  The `t` of the database state is taken from the path param `vid`."
  [handler node timeout]
  (fn [{{:keys [vid]} :path-params :as request}]
    (if (:blaze/db request)
      (handler request)
      (if-let [t (some-> vid fhir-util/parse-nat-long)]
        (-> (fhir-util/sync node t timeout)
            (ac/then-compose #(handler (assoc request :blaze/db %))))
        (ac/completed-future
         (ba/incorrect
          (format "Resource versionId `%s` is invalid." vid)
          :fhir/issue "value"
          :fhir/operation-outcome "MSG_ID_INVALID"))))))
