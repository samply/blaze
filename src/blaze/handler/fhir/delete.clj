(ns blaze.handler.fhir.delete
  "FHIR delete interaction.

  https://www.hl7.org/fhir/http.html#delete"
  (:require
    [blaze.datomic.transaction :as tx]
    [blaze.datomic.util :as util]
    [blaze.handler.util :as handler-util]
    [blaze.middleware.exception :refer [wrap-exception]]
    [blaze.middleware.json :refer [wrap-json]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [manifold.deferred :as md]
    [ring.util.response :as ring]
    [ring.util.time :as ring-time]))


(defn- exists-resource? [db type id]
  (and (util/cached-entity db (keyword type))
       (d/entity db [(keyword type "id") id])))


(defn- delete-resource
  [conn type id & {:keys [max-retries] :or {max-retries 5}}]
  (md/loop [retried 0
            db (d/db conn)]
    (-> (tx/transact-async conn (tx/resource-deletion db type id))
        (md/catch'
          (fn [{::anom/keys [category] :as anomaly}]
            (if (and (< retried max-retries) (= ::anom/conflict category))
              (-> (d/sync conn (inc (d/basis-t db)))
                  (md/chain #(md/recur (inc retried) %)))
              (md/error-deferred anomaly)))))))


(defn handler-intern [conn]
  (fn [{{:keys [type id]} :route-params}]
    (if (exists-resource? (d/db conn) type id)
      (-> (delete-resource conn type id)
          (md/chain'
            (fn [{db :db-after}]
              (let [last-modified (:db/txInstant (util/basis-transaction db))]
                (-> (ring/response nil)
                    (ring/status 204)
                    (ring/header "Last-Modified" (ring-time/format-date last-modified))
                    (ring/header "ETag" (str "W/\"" (d/basis-t db) "\"")))))))
      (handler-util/error-response
        {::anom/category ::anom/not-found
         :fhir/issue "not-found"}))))


(s/def :handler.fhir/delete fn?)


(s/fdef handler
  :args (s/cat :conn ::ds/conn)
  :ret :handler.fhir/delete)

(defn handler
  ""
  [conn]
  (-> (handler-intern conn)
      (wrap-json)
      (wrap-exception)))
