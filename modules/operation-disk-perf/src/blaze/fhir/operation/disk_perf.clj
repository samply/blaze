(ns blaze.fhir.operation.disk-perf
  "Main entry point into the $disk-perf operation."
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.handler.util :as handler-util]
   [blaze.job-scheduler :as js]
   [blaze.job.disk-perf :as job-disk-perf]
   [blaze.module :as m]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [java-time.api :as time]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(defn- parameters [{:fhir/keys [type] :as body}]
  (cond
    (nil? body) nil
    (= :fhir/Parameters type) body
    :else (ba/incorrect (format "Expected Parameters resource but was `%s` resource." (name type)))))

(defn- opt-param [{:keys [parameter]} name]
  (some #(when (= name (-> % :name :value)) (-> % :value :value)) parameter))

(defn- job-params
  "Extracts the optional job parameters from the Parameters resource. Missing
  parameters are left out so that the job runs with its defaults."
  [parameters]
  (reduce
   (fn [ret [key name]]
     (if-some [value (opt-param parameters name)]
       (assoc ret key value)
       ret))
   {}
   [[:database "database"]
    [:file-size "file-size"]
    [:phase-duration "phase-duration"]
    [:max-concurrency "max-concurrency"]]))

(defmethod m/pre-init-spec :blaze.fhir.operation/disk-perf [_]
  (s/keys :req-un [:blaze/clock] :opt-un [:blaze/context-path]))

(defmethod ig/init-key :blaze.fhir.operation/disk-perf
  [_ {:keys [clock] :as context}]
  (log/info "Init FHIR $disk-perf operation handler")
  (fn [{:keys [body] :blaze/keys [job-scheduler] :as request}]
    (if-ok [parameters (parameters body)]
      (let [authored-on (time/offset-date-time clock)]
        (log/debug "Initiate async response...")
        (do-sync [job (js/create-job job-scheduler (job-disk-perf/job authored-on (job-params parameters)))]
          (-> (ring/status 202)
              (handler-util/async-status-location context request job))))
      ac/completed-future)))
