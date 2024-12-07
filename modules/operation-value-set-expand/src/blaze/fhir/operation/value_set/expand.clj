(ns blaze.fhir.operation.value-set.expand
  "Main entry point into the ValueSet $expand operation."
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.module :as m]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service.spec]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [ring.util.response :as ring]
   [taoensso.timbre :as log])
  (:import
   [com.google.common.base CaseFormat]))

(set! *warn-on-reflection* true)

(defn camel->kebab [s]
  (.to CaseFormat/LOWER_CAMEL CaseFormat/LOWER_HYPHEN s))

(defn- parse-nat-long [value]
  (when-let [value (parse-long value)]
    (when-not (neg? value)
      value)))

(defn- validate-params [params]
  (reduce
   (fn [params {:keys [name action]}]
     (if-let [value (get params name)]
       (case action
         :copy
         (-> (dissoc params name)
             (assoc (keyword (camel->kebab name)) value))

         :parse-nat-long
         (if-let [value (parse-nat-long value)]
           (-> (dissoc params name)
               (assoc (keyword (camel->kebab name)) value))
           (reduced (ba/incorrect (format "Invalid value for parameter `%s`. Has to be a non-negative integer." name))))

         :parse-boolean
         (if-let [value (parse-boolean value)]
           (-> (dissoc params name)
               (assoc (keyword (camel->kebab name)) value))
           (reduced (ba/incorrect (format "Invalid value for parameter `%s`. Has to be a boolean." name))))

         (reduced (ba/unsupported (format "Unsupported parameter `%s`." name)
                                  :http/status 400)))
       params))
   params
   [{:name "url" :action :copy}
    {:name "valueSetVersion" :action :copy}
    {:name "context"}
    {:name "contextDirection"}
    {:name "filter"}
    {:name "date"}
    {:name "offset"}
    {:name "count" :action :parse-nat-long}
    {:name "includeDesignations"}
    {:name "designation"}
    {:name "includeDefinition" :action :parse-boolean}
    {:name "activeOnly"}
    {:name "useSupplement"}
    {:name "excludeNested"}
    {:name "excludeNotForUI"}
    {:name "excludePostCoordinated"}
    {:name "displayLanguage"}
    {:name "property"}
    {:name "exclude-system"}
    {:name "system-version"}
    {:name "check-system-version"}
    {:name "force-system-version"}]))

(defn- expand-value-set
  [terminology-service
   {{:keys [id]} :path-params {:strs [url] :as params} :query-params}]
  (if-ok [params (validate-params params)]
    (cond
      id (ts/expand-value-set terminology-service (assoc params :id id))
      url (ts/expand-value-set terminology-service params)
      :else (ac/completed-future (ba/incorrect "Missing required parameter `url`.")))
    ac/completed-future))

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
