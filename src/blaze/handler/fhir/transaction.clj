(ns blaze.handler.fhir.transaction
  "FHIR batch/transaction endpoint.

  https://www.hl7.org/fhir/http.html#transaction"
  (:require
    [clojure.spec.alpha :as s]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [blaze.datomic.transaction :as tx]
    [blaze.middleware.exception :refer [wrap-exception]]
    [blaze.middleware.json :refer [wrap-json]]
    [manifold.deferred :as md]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(defn- bundle-tx-data [db bundle]
  (into
    []
    (comp
      (map #(get % "resource"))
      (mapcat #(tx/resource-update db (dissoc % "meta" "text"))))
    (get bundle "entry")))


(defn- transact [conn bundle]
  (->> (bundle-tx-data (d/db conn) bundle)
       (d/transact-async conn)))


(defn- handler-intern [conn]
  (fn [{:keys [body]}]
    (-> (transact conn body)
        (md/chain
          (fn [{:keys [db-after]}]
            (-> (ring/response {:message "OK" :t (d/basis-t db-after)})
                (ring/status 200))))
        (md/catch
          (fn [^Throwable e]
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
  :args (s/cat :conn ::ds/conn)
  :ret :handler.fhir/transaction)

(defn handler
  ""
  [conn]
  (-> (handler-intern conn)
      (wrap-exception)
      (wrap-json)
      (wrap-post)))
