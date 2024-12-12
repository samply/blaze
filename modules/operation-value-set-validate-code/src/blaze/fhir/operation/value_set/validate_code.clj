(ns blaze.fhir.operation.value-set.validate-code
  "Main entry point into the ValueSet $validate-code operation."
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.fhir.spec.type :as type]
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

(def ^:private parameter-specs
  [{:name "url" :action :copy}
   {:name "context"}
   {:name "valueSet" :action :copy-resource}
   {:name "valueSetVersion"}
   {:name "code" :action :copy}
   {:name "system" :action :copy}
   {:name "systemVersion"}
   {:name "display"}
   {:name "coding" :action :copy-complex-type}
   {:name "codeableConcept"}
   {:name "date"}
   {:name "abstract"}
   {:name "displayLanguage" :action :copy}
   {:name "useSupplement"}
   {:name "inferSystem" :action :copy}])

(defn camel->kebab [s]
  (.to CaseFormat/LOWER_CAMEL CaseFormat/LOWER_HYPHEN s))

(defn- validate-query-params [params]
  (reduce
   (fn [new-params {:keys [name action]}]
     (if-let [value (get params name)]
       (case action
         :copy
         (assoc new-params (keyword (camel->kebab name)) value)

         (:copy-complex-type :copy-resource)
         (reduced (ba/unsupported (format "Unsupported parameter `%s` in GET request. Please use POST." name)
                                  :http/status 400))

         (reduced (ba/unsupported (format "Unsupported parameter `%s`." name)
                                  :http/status 400)))
       new-params))
   {}
   parameter-specs))

(defn- validate-body-params [{params :parameter}]
  (reduce
   (fn [new-params {:keys [name action]}]
     (if-let [param (some #(when (= name (type/value (:name %))) %) params)]
       (case action
         :copy
         (assoc new-params (keyword (camel->kebab name)) (type/value (:value param)))

         :copy-complex-type
         (assoc new-params (keyword (camel->kebab name)) (:value param))

         :copy-resource
         (assoc new-params (keyword (camel->kebab name)) (:resource param))

         (reduced (ba/unsupported (format "Unsupported parameter `%s`." name)
                                  :http/status 400)))
       new-params))
   {}
   parameter-specs))

(defn- validate-more [{:keys [code system infer-system] :as params}]
  (if (and code (not infer-system) (nil? system))
    (ba/incorrect "Missing required parameter `system`.")
    params))

(defn- validate-params [{:keys [request-method body query-params]}]
  (when-ok [params (if (= :post request-method)
                     (validate-body-params body)
                     (validate-query-params query-params))]
    (validate-more params)))

(defn- validate-code
  [terminology-service {{:keys [id]} :path-params :as request}]
  (if-ok [{:keys [url value-set] :as params} (validate-params request)]
    (cond
      id (ts/value-set-validate-code terminology-service (assoc params :id id))
      url (ts/value-set-validate-code terminology-service params)
      value-set (ts/value-set-validate-code terminology-service params)
      :else (ac/completed-future (ba/incorrect "Missing required parameter `url`.")))
    ac/completed-future))

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
