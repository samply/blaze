(ns blaze.middleware.fhir.db
  "This middleware provides a database value for read-only interactions.

  It uses the optional query param __t and vid from path to acquire the right
  database value."
  (:require
    [blaze.async.comp :as ac :refer [do-sync]]
    [blaze.db.api :as d]
    [blaze.handler.fhir.util :as fhir-util])
  (:import
    [java.util.concurrent TimeUnit]))


(defn- vid [{{:keys [vid]} :path-params}]
  (and vid (re-matches #"\d+" vid) (Long/parseLong vid)))


(defn- db [node timeout {:keys [query-params] :as request}]
  (if-let [t (vid request)]
    (do-sync [db (d/sync node t)]
      (d/as-of db t))
    (if-let [t (fhir-util/t query-params)]
      (d/sync node t)
      (ac/or-timeout! (d/sync node) timeout TimeUnit/MILLISECONDS))))


(defn wrap-db
  ([handler node]
   (wrap-db handler node 10000))
  ([handler node timeout]
   (fn [request]
     (if (:blaze/db request)
       (handler request)
       (-> (db node timeout request)
           (ac/then-compose #(handler (assoc request :blaze/db %))))))))
