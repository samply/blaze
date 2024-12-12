(ns blaze.fhir.operation.value-set.expand
  "Main entry point into the ValueSet $expand operation."
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
   {:name "valueSet" :action :copy-resource}
   {:name "valueSetVersion" :action :copy}
   {:name "context"}
   {:name "contextDirection"}
   {:name "filter"}
   {:name "date"}
   {:name "offset" :action :parse-nat-long}
   {:name "count" :action :parse-nat-long}
   {:name "includeDesignations"}
   {:name "designation"}
   {:name "includeDefinition" :action :parse-boolean}
   {:name "activeOnly" :action :parse-boolean}
   {:name "useSupplement"}
   {:name "excludeNested" :action :parse-boolean}
   {:name "excludeNotForUI"}
   {:name "excludePostCoordinated"}
   {:name "displayLanguage" :action :copy}
   {:name "property"}
   {:name "exclude-system"}
   {:name "system-version" :action :parse-canonical :cardinality :many}
   {:name "check-system-version"}
   {:name "force-system-version"}])

(defn camel->kebab [s]
  (.to CaseFormat/LOWER_CAMEL CaseFormat/LOWER_HYPHEN s))

(defn- parse-nat-long [value]
  (when-let [value (parse-long value)]
    (when-not (neg? value)
      value)))

(defn- assoc-via [params {:keys [name cardinality]} value]
  (if (identical? :many cardinality)
    (update params (keyword (camel->kebab name)) (fnil conj []) value)
    (assoc params (keyword (camel->kebab name)) value)))

(defn- validate-query-params [params]
  (reduce
   (fn [new-params {:keys [name action] :as spec}]
     (if-let [value (get params name)]
       (case action
         :copy
         (assoc-via new-params spec value)

         :parse-nat-long
         (if-let [value (parse-nat-long value)]
           (assoc-via new-params spec value)
           (reduced (ba/incorrect (format "Invalid value for parameter `%s`. Has to be a non-negative integer." name))))

         :parse-boolean
         (if-let [value (parse-boolean value)]
           (assoc-via new-params spec value)
           (reduced (ba/incorrect (format "Invalid value for parameter `%s`. Has to be a boolean." name))))

         :parse-canonical
         (assoc-via new-params spec (type/canonical value))

         :copy-resource
         (reduced (ba/unsupported (format "Unsupported parameter `%s` in GET request. Please use POST." name)
                                  :http/status 400))

         (reduced (ba/unsupported (format "Unsupported parameter `%s`." name)
                                  :http/status 400)))
       new-params))
   {}
   parameter-specs))

(defn- validate-body-params [{params :parameter}]
  (reduce
   (fn [new-params {:keys [name action] :as spec}]
     (if-let [param (some #(when (= name (type/value (:name %))) %) params)]
       (case action
         (:copy :parse-boolean)
         (assoc-via new-params spec (type/value (:value param)))

         :parse-nat-long
         (let [value (type/value (:value param))]
           (if-not (neg? value)
             (assoc-via new-params spec value)
             (reduced (ba/incorrect (format "Invalid value for parameter `%s`. Has to be a non-negative integer." name)))))

         :parse-canonical
         (assoc-via new-params spec (:value param))

         :copy-resource
         (assoc-via new-params spec (:resource param))

         (reduced (ba/unsupported (format "Unsupported parameter `%s`." name)
                                  :http/status 400)))
       new-params))
   {}
   parameter-specs))

(defn- validate-more [{:keys [offset] :as params}]
  (if (and (some? offset) (not (zero? offset)))
    (ba/incorrect "Invalid non-zero value for parameter `offset`.")
    params))

(defn- validate-params [{:keys [request-method body query-params]}]
  (when-ok [params (if (= :post request-method)
                     (validate-body-params body)
                     (validate-query-params query-params))]
    (validate-more params)))

(defn- expand-value-set
  [terminology-service {{:keys [id]} :path-params :as request}]
  (if-ok [{:keys [url value-set] :as params} (validate-params request)]
    (cond
      id (ts/expand-value-set terminology-service (assoc params :id id))
      url (ts/expand-value-set terminology-service params)
      value-set (ts/expand-value-set terminology-service params)
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
