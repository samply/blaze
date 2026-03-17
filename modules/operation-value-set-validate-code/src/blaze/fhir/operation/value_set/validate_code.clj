(ns blaze.fhir.operation.value-set.validate-code
  "Main entry point into the ValueSet $validate-code operation."
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
  {"url" {:action :copy :coerce #(type/uri %2)}
   "context" {}
   "valueSet" {:action :complex}
   "valueSetVersion" {:action :copy :coerce #(type/string %2)}
   "code" {:action :copy :coerce #(type/code %2)}
   "system" {:action :copy :coerce #(type/uri %2)}
   "systemVersion" {:action :copy :coerce #(type/string %2)}
   "display" {:action :copy :coerce #(type/string %2)}
   "coding" {:action :complex}
   "codeableConcept" {:action :complex}
   "date" {}
   "abstract" {}
   "displayLanguage" {:action :copy :coerce #(type/string %2)}
   "useSupplement" {}
   "inferSystem" {:action :copy :coerce fu/coerce-boolean}
   "system-version" {:action :copy :coerce #(type/canonical %2)}
   "tx-resource" {:action :complex}})

(defn- contains-param? [name {:keys [parameter]}]
  (some (comp #{name} :value :name) parameter))

(defn- body-params [{:keys [body] {:strs [accept-language]} :headers}]
  (cond-> body
    (and accept-language (not (contains-param? "displayLanguage" body)))
    (update :parameter (fnil conj []) (fu/parameter "displayLanguage" (type/string accept-language)))))

(defn- params-from-headers [{:strs [accept-language]}]
  (cond-> {}
    accept-language (assoc "displayLanguage" accept-language)))

(defn- query-params [{:keys [headers query-params]}]
  (merge (params-from-headers headers) query-params))

(defn- validate-params* [{:keys [request-method] :as request}]
  (if (= :post request-method)
    (body-params request)
    (fu/validate-query-params parameter-specs (query-params request))))

(defn- validate-params [{{:keys [id]} :path-params :blaze/keys [db] :as request}]
  (if-ok [params (validate-params* request)]
    (if id
      (do-sync [{:keys [url]} (fhir-util/pull db "ValueSet" id :summary)]
        (update params :parameter (fnil conj []) (fu/parameter "url" url)))
      (ac/completed-future params))
    ac/completed-future))

(defn- validate-code* [terminology-service params]
  (-> (ts/value-set-validate-code terminology-service params)
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

(defmethod m/pre-init-spec :blaze.fhir.operation.value-set/validate-code [_]
  (s/keys :req-un [:blaze/terminology-service]))

(defmethod ig/init-key :blaze.fhir.operation.value-set/validate-code
  [_ {:keys [terminology-service]}]
  (log/info "Init FHIR ValueSet $validate-code operation handler")
  (handler terminology-service))
