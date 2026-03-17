(ns blaze.fhir.operation.code-system.lookup
  "Main entry point into the CodeSystem $lookup operation."
  (:require
   [blaze.anomaly :refer [if-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.util :as fu]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.module :as m]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(def ^:private parameter-specs
  {"code" {:action :copy :coerce #(type/code %2)}
   "system" {:action :copy :coerce #(type/uri %2)}
   "version" {:action :copy :coerce #(type/string %2)}
   "coding" {:action :complex}
   "date" {}
   "displayLanguage" {}
   "property" {}
   "useSupplement" {}
   "tx-resource" {:action :complex}})

(defn- validate-params* [{:keys [request-method body query-params]}]
  (if (= :post request-method)
    body
    (fu/validate-query-params parameter-specs query-params)))

(defn- validate-params [{{:keys [id]} :path-params :blaze/keys [db] :as request}]
  (if-ok [params (validate-params* request)]
    (if id
      (do-sync [{:keys [url version]} (fhir-util/pull db "CodeSystem" id :summary)]
        (update params :parameter
                (fnil conj [])
                (fu/parameter "system" url)
                (fu/parameter "version" version)))
      (ac/completed-future params))
    ac/completed-future))

(defn- lookup* [terminology-service params]
  (-> (ts/code-system-lookup terminology-service params)
      (ac/exceptionally
       (fn [{::anom/keys [category] :as anomaly}]
         (cond-> anomaly (= ::anom/not-found category) (assoc :http/status 400))))))

(defn- lookup [terminology-service request]
  (-> (validate-params request)
      (ac/then-compose (partial lookup* terminology-service))))

(defn- handler [terminology-service]
  (fn [request]
    (do-sync [response (lookup terminology-service request)]
      (ring/response response))))

(defmethod m/pre-init-spec :blaze.fhir.operation.code-system/lookup [_]
  (s/keys :req-un [:blaze/terminology-service]))

(defmethod ig/init-key :blaze.fhir.operation.code-system/lookup
  [_ {:keys [terminology-service]}]
  (log/info "Init FHIR CodeSystem $lookup operation handler")
  (handler terminology-service))
