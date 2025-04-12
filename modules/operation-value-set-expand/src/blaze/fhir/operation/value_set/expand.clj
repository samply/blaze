(ns blaze.fhir.operation.value-set.expand
  "Main entry point into the ValueSet $expand operation."
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.module :as m]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service.spec]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(defn- coerce-boolean [name value]
  (if-some [value (parse-boolean value)]
    (type/boolean value)
    (ba/incorrect (format "Invalid value for parameter `%s`. Has to be a boolean." name))))

(defn- coerce-canonical [_ value]
  (type/canonical value))

(defn- coerce-code [_ value]
  (type/code value))

(defn- coerce-integer [name value]
  (if-let [value (parse-long value)]
    (type/integer value)
    (ba/incorrect (format "Invalid value for parameter `%s`. Has to be an integer." name))))

(defn- coerce-string [_ value]
  (type/string value))

(defn- coerce-uri [_ value]
  (type/uri value))

(def ^:private parameter-specs
  {"url" {:action :copy :coerce coerce-uri}
   "valueSet" {:action :complex}
   "valueSetVersion" {:action :copy :coerce coerce-string}
   "context" {}
   "contextDirection" {}
   "filter" {}
   "date" {}
   "offset" {:action :copy :coerce coerce-integer}
   "count" {:action :copy :coerce coerce-integer}
   "includeDesignations" {:action :copy :coerce coerce-boolean}
   "designation" {}
   "includeDefinition" {:action :copy :coerce coerce-boolean}
   "activeOnly" {:action :copy :coerce coerce-boolean}
   "useSupplement" {}
   "excludeNested" {:action :copy :coerce coerce-boolean}
   "excludeNotForUI" {}
   "excludePostCoordinated" {}
   "displayLanguage" {:action :copy :coerce coerce-code}
   "property" {:action :copy :coerce coerce-string}
   "exclude-system" {}
   "system-version" {:action :copy :coerce coerce-canonical}
   "check-system-version" {}
   "force-system-version" {}
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
         (if-ok [value (coerce name value)]
           (conj new-params (parameter name value))
           reduced)

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
      (do-sync [{:keys [url]} (fhir-util/pull db "ValueSet" id :summary)]
        (update params :parameter (fnil conj []) (parameter "url" url)))
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
