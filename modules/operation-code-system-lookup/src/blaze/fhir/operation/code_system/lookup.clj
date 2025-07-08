(ns blaze.fhir.operation.code-system.lookup
  "Main entry point into the CodeSystem $lookup operation."
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.fhir.spec.type :as type]
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
  {"url" {:action :copy :coerce type/uri}
   "codeSystem" {:action :complex}
   "code" {:action :copy :coerce type/code}
   "version" {:action :copy :coerce type/string}
   "display" {:action :copy :coerce type/string}
   "coding" {:action :complex}
   "codeableConcept" {:action :complex}
   "date" {}
   "abstract" {}
   "displayLanguage" {:action :copy :coerce type/code}
   "tx-resource" {:action :complex}})

(defn- parameter [name value]
  {:fhir/type :fhir.Parameters/parameter
   :name (type/string name)
   :value value})

(defn- validate-query-params [params]
  (reduce-kv
   (fn [new-params name value]
     (if-let [{:keys [action coerce]} (parameter-specs name)]
       (case action
         :copy
         (conj new-params (parameter name (coerce value)))

         :complex
         (reduced (ba/unsupported (format "Unsupported parameter `%s` in GET request. Please use POST." name)
                                  :http/status 400))

         (reduced (ba/unsupported (format "Unsupported parameter `%s`." name)
                                  :http/status 400)))
       new-params))
   []
   params))

(defn- validate-params* [{:keys [request-method body query-params]}]
  (if (= :post request-method)
    body
    (when-ok [params (validate-query-params query-params)]
             {:fhir/type :fhir/Parameters :parameter params})))

(defn- validate-params [{{:keys [id]} :path-params :blaze/keys [db] :as request}]
  (if-ok [params (validate-params* request)]
         (if id
           (do-sync [{:keys [url]} (fhir-util/pull db "CodeSystem" id :summary)]
             (update params :parameter (fnil conj []) (parameter "url" url)))
           (ac/completed-future params))
         ac/completed-future))

(defn- lookup* [terminology-service params]
  (-> (ts/code-system-validate-code terminology-service params)
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
