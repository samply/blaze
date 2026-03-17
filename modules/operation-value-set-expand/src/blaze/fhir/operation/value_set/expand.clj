(ns blaze.fhir.operation.value-set.expand
  "Main entry point into the ValueSet $expand operation."
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
   [integrant.core :as ig]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(def ^:private parameter-specs
  {"url" {:action :copy :coerce #(type/uri %2)}
   "valueSet" {:action :complex}
   "valueSetVersion" {:action :copy :coerce #(type/string %2)}
   "context" {}
   "contextDirection" {}
   "filter" {}
   "date" {}
   "offset" {:action :copy :coerce fu/coerce-integer}
   "count" {:action :copy :coerce fu/coerce-integer}
   "includeDesignations" {:action :copy :coerce fu/coerce-boolean}
   "designation" {}
   "includeDefinition" {:action :copy :coerce fu/coerce-boolean}
   "activeOnly" {:action :copy :coerce fu/coerce-boolean}
   "useSupplement" {}
   "excludeNested" {:action :copy :coerce fu/coerce-boolean}
   "excludeNotForUI" {}
   "excludePostCoordinated" {}
   "displayLanguage" {:action :copy :coerce #(type/code %2)}
   "property" {:action :copy :coerce #(type/string %2)}
   "exclude-system" {}
   "system-version" {:action :copy :coerce #(type/canonical %2)}
   "check-system-version" {}
   "force-system-version" {}
   "tx-resource" {:action :complex}})

(defn- validate-params* [{:keys [request-method body query-params]}]
  (if (= :post request-method)
    body
    (fu/validate-query-params parameter-specs query-params)))

(defn- validate-params [{{:keys [id]} :path-params :blaze/keys [db] :as request}]
  (if-ok [params (validate-params* request)]
    (if id
      (do-sync [{:keys [url]} (fhir-util/pull db "ValueSet" id :summary)]
        (update params :parameter (fnil conj []) (fu/parameter "url" url)))
      (ac/completed-future params))
    ac/completed-future))

(defn- expand-value-set [terminology-service request]
  (-> (validate-params request)
      (ac/then-compose (partial ts/expand-value-set terminology-service))))

(defn- handler [terminology-service]
  (fn [request]
    (do-sync [value-set (expand-value-set terminology-service request)]
      (ring/response value-set))))

(defmethod m/pre-init-spec :blaze.fhir.operation.value-set/expand [_]
  (s/keys :req-un [:blaze/terminology-service]))

(defmethod ig/init-key :blaze.fhir.operation.value-set/expand
  [_ {:keys [terminology-service]}]
  (log/info "Init FHIR ValueSet $expand operation handler")
  (handler terminology-service))
