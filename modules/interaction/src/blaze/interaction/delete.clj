(ns blaze.interaction.delete
  "FHIR delete interaction.

  https://www.hl7.org/fhir/http.html#delete"
  (:require
    [blaze.datomic.util :as util]
    [blaze.handler.fhir.util :as handler-fhir-util]
    [blaze.handler.util :as handler-util]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [manifold.deferred :as md]
    [ring.util.response :as ring]
    [ring.util.time :as ring-time]))


(defn- handler-intern [conn]
  (fn [{{:keys [type id]} :path-params}]
    (let [db (d/db conn)]
      (if (util/resource db type id)
        (-> (handler-fhir-util/delete-resource conn db type id)
            (md/chain'
              (fn [{db :db-after}]
                (let [last-modified (:db/txInstant (util/basis-transaction db))]
                  (-> (ring/response nil)
                      (ring/status 204)
                      (ring/header "Last-Modified" (ring-time/format-date last-modified))
                      (ring/header "ETag" (str "W/\"" (d/basis-t db) "\"")))))))
        (handler-util/error-response
          {::anom/category ::anom/not-found
           :fhir/issue "not-found"})))))


(s/def :handler.fhir/delete fn?)


(s/fdef handler
  :args (s/cat :conn ::ds/conn)
  :ret :handler.fhir/delete)

(defn handler
  ""
  [conn]
  (-> (handler-intern conn)
      (wrap-observe-request-duration "delete")))
