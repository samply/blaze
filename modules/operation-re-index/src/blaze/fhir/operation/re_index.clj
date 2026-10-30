(ns blaze.fhir.operation.re-index
  "Main entry point into the $re-index operation."
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.search-param-registry :as sr]
   [blaze.db.search-param-registry.spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.util :as fu]
   [blaze.handler.util :as handler-util]
   [blaze.job-scheduler :as js]
   [blaze.job.re-index :as job-re-index]
   [blaze.module :as m]
   [blaze.spec]
   [blaze.time :as bt]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(defn- parameters [{:fhir/keys [type] :as body}]
  (cond
    (nil? body) {:fhir/type :fhir/Parameters}
    (= :fhir/Parameters type) body
    :else (ba/incorrect (format "Expected Parameters resource but was `%s` resource." (name type)))))

(defn- coerce-search-param-url [search-param-registry]
  (fn [value]
    (if (or (type/canonical? value) (type/uri? value))
      (let [url (:value value)]
        (if (sr/get-by-url search-param-registry url)
          url
          (ba/incorrect (format "Search parameter with URL `%s` not found." url))))
      (ba/incorrect "Has to be a canonical."))))

(defn- param-specs
  "Specs of the job parameters."
  [search-param-registry]
  {"search-param-url"
   {:action :copy
    :required true
    :coerce (coerce-search-param-url search-param-registry)}})

(defmethod m/pre-init-spec :blaze.fhir.operation/re-index [_]
  (s/keys :req-un [:blaze.db/search-param-registry :blaze/clock]
          :opt-un [:blaze/context-path]))

(defmethod ig/init-key :blaze.fhir.operation/re-index
  [_ {:keys [search-param-registry clock] :as context}]
  (log/info "Init FHIR $re-index operation handler")
  (let [param-specs (param-specs search-param-registry)]
    (fn [{:keys [body] :blaze/keys [job-scheduler] :as request}]
      (if-ok [parameters (parameters body)
              params (fu/coerce-params param-specs parameters)]
        (let [authored-on (bt/offset-date-time clock)]
          (log/debug "Initiate async response...")
          (do-sync [job (js/create-job job-scheduler (job-re-index/job authored-on params))]
            (-> (ring/status 202)
                (handler-util/async-status-location context request job))))
        ac/completed-future))))
