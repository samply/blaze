(ns blaze.middleware.fhir.db
  "This middleware provides a database value for read-only interactions.

  It uses the optional query param __t and vid from path to acquire the right
  database value."
  (:require
    [blaze.anomaly :as ba]
    [blaze.async.comp :as ac]
    [blaze.db.api :as d]
    [blaze.handler.fhir.util :as fhir-util]
    [cognitect.anomalies :as anom])
  (:import
    [java.util.concurrent TimeoutException TimeUnit]))


(defn- vid [{{:keys [vid]} :path-params}]
  (and vid (re-matches #"\d+" vid) (Long/parseLong vid)))


(defn- db [node {:keys [query-params] :as request}]
  (if-let [t (vid request)]
    (-> (d/sync node t) (ac/then-apply #(d/as-of % t)))
    (if-let [t (fhir-util/t query-params)]
      (d/sync node t)
      (ac/or-timeout! (d/sync node) 2 TimeUnit/SECONDS))))


(defn wrap-db [handler node]
  (fn [request]
    (if (:blaze/db request)
      (handler request)
      (-> (db node request)
          (ac/then-compose #(handler (assoc request :blaze/db %)))
          (ac/exceptionally
            (fn [e]
              (condp identical? (class (ex-cause e))
                TimeoutException
                (ba/throw-anom ::anom/busy "")
                (throw e))))))))
