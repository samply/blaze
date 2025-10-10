(ns blaze.fhir.operation.value-set.validate-code
  "Main entry point into the ValueSet $validate-code operation."
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

(defn- coerce-boolean [name value]
  (if-some [value (parse-boolean value)]
    (type/boolean value)
    (ba/incorrect (format "Invalid value for parameter `%s`. Has to be a boolean." name))))

(defn- coerce-canonical [_ value]
  (type/canonical value))

(defn- coerce-code [_ value]
  (type/code value))

(defn- coerce-string [_ value]
  (type/string value))

(defn- coerce-uri [_ value]
  (type/uri value))

(def ^:private parameter-specs
  {"url" {:action :copy :coerce coerce-uri}
   "context" {}
   "valueSet" {:action :complex}
   "valueSetVersion" {:action :copy :coerce coerce-string}
   "code" {:action :copy :coerce coerce-code}
   "system" {:action :copy :coerce coerce-uri}
   "systemVersion" {:action :copy :coerce coerce-string}
   "display" {:action :copy :coerce coerce-string}
   "coding" {:action :complex}
   "codeableConcept" {:action :complex}
   "date" {}
   "abstract" {}
   "displayLanguage" {:action :copy :coerce coerce-string}
   "useSupplement" {}
   "inferSystem" {:action :copy :coerce coerce-boolean}
   "system-version" {:action :copy :coerce coerce-canonical}
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

(defn- contains-param? [name {:keys [parameter]}]
  (some (comp #{name} :value :name) parameter))

(defn- body-params [{:keys [body] {:strs [accept-language]} :headers}]
  (cond-> body
    (and accept-language (not (contains-param? "displayLanguage" body)))
    (update :parameter (fnil conj []) (parameter "displayLanguage" (type/string accept-language)))))

(defn- params-from-headers [{:strs [accept-language]}]
  (cond-> {}
    accept-language (assoc "displayLanguage" accept-language)))

(defn- query-params [{:keys [headers query-params]}]
  (merge (params-from-headers headers) query-params))

(defn- validate-params* [{:keys [request-method] :as request}]
  (if (= :post request-method)
    (body-params request)
    (when-ok [params (validate-query-params (query-params request))]
      {:fhir/type :fhir/Parameters :parameter params})))

(defn- validate-params [{{:keys [id]} :path-params :blaze/keys [db] :as request}]
  (if-ok [params (validate-params* request)]
    (if id
      (do-sync [{:keys [url]} (fhir-util/pull db "ValueSet" id :summary)]
        (update params :parameter (fnil conj []) (parameter "url" url)))
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
