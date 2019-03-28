(ns life-fhir-store.handler.fhir.transaction
  "FHIR batch/transaction endpoint.

  https://www.hl7.org/fhir/http.html#transaction"
  (:require
    [cheshire.core :as json]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [life-fhir-store.datomic.transaction :as tx]
    [life-fhir-store.middleware.exception :refer [wrap-exception]]
    [life-fhir-store.middleware.json :refer [wrap-json]]
    [manifold.deferred :as md]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(defn- update-tx-data
  [structure-definitions db {:keys [resourceType id] :as resource}]
  (assert resourceType)
  (let [old (d/pull db '[*] [(keyword resourceType "id") id])
        old (cond-> old (nil? (:db/id old)) (assoc :db/id (d/tempid (keyword "life.part" resourceType))))]
    (tx/update-tx-data structure-definitions old (dissoc resource :meta :text))))


(defn- bundle-tx-data [db structure-definitions bundle]
  (into
    []
    (comp
      (map :resource)
      (mapcat #(update-tx-data structure-definitions db %)))
    (:entry bundle)))


(defn- transact [conn structure-definitions bundle]
  (->> (bundle-tx-data (d/db conn) structure-definitions bundle)
       (d/transact-async conn)))


(defn- handler-intern [conn structure-definitions]
  (fn [{:keys [body]}]
    (-> (transact conn structure-definitions body)
        (md/chain
          (fn [{:keys [db-after]}]
            (-> (ring/response {:message "OK" :t (d/basis-t db-after)})
                (ring/status 200))))
        (md/catch
          (fn [e]
            (log/error (.getMessage e) e)
            (-> (ring/response {:error (.getMessage e)})
                (ring/status 500)))))))


(defn- wrap-post [handler]
  (fn [{:keys [request-method] :as request}]
    (if (= :post request-method)
      (handler request)
      (-> (ring/response "")
          (ring/status 405)))))


(s/def :handler.fhir/transaction fn?)


(s/fdef handler
  :args (s/cat :conn ::ds/conn :structure-definitions map?)
  :ret :handler.fhir/transaction)

(defn handler
  ""
  [conn structure-definitions]
  (-> (handler-intern conn structure-definitions)
      (wrap-exception)
      (wrap-json)
      (wrap-post)))
