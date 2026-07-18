(ns blaze.fhir.operation.code-system.validate-code
  "Main entry point into the CodeSystem $validate-code operation."
  (:require
   [blaze.anomaly :refer [if-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.util :as fu]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.module :as m]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service.spec]
   [blaze.util :refer [conj-vec]]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(def ^:private parameter-specs
  {"url" {:action :copy :coerce #(type/uri %2)}
   "codeSystem" {:action :complex}
   "code" {:action :copy :coerce #(type/code %2)}
   "version" {:action :copy :coerce #(type/string %2)}
   "display" {:action :copy :coerce #(type/string %2)}
   "coding" {:action :complex}
   "codeableConcept" {:action :complex}
   "date" {}
   "abstract" {}
   "displayLanguage" {:action :copy :coerce #(type/code %2)}
   "tx-resource" {:action :complex}})

(defn- validate-params* [{:keys [request-method body query-params]}]
  (if (= :post request-method)
    body
    (fu/validate-query-params parameter-specs query-params)))

(defn- validate-params [{{:keys [id]} :path-params :blaze/keys [db] :as request}]
  (if-ok [params (validate-params* request)]
    (if id
      (do-sync [{:keys [url]} (fhir-util/pull db "CodeSystem" id :summary)]
        (update params :parameter conj-vec (fu/parameter "url" url)))
      (ac/completed-future params))
    ac/completed-future))

(defn- validate-code* [terminology-service params]
  (-> (ts/code-system-validate-code terminology-service params)
      (ac/exceptionally
       (fn [{::anom/keys [category] :as anomaly}]
         (cond-> anomaly (= ::anom/not-found category) (assoc :http/status 400))))))

(defn- validate-code [terminology-service request]
  (-> (validate-params request)
      (ac/then-compose (partial validate-code* terminology-service))))

(defn- handler [terminology-service]
  (fn [request]
    (do-sync [response (validate-code terminology-service request)]
      (ring/response response))))

(defmethod m/pre-init-spec :blaze.fhir.operation.code-system/validate-code [_]
  (s/keys :req-un [:blaze/terminology-service]))

(defmethod ig/init-key :blaze.fhir.operation.code-system/validate-code
  [_ {:keys [terminology-service]}]
  (log/info "Init FHIR CodeSystem $validate-code operation handler")
  (handler terminology-service))
