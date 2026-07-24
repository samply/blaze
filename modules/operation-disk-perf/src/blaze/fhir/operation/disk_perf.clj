(ns blaze.fhir.operation.disk-perf
  "Main entry point into the $disk-perf operation."
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.fhir.util :as fu]
   [blaze.handler.util :as handler-util]
   [blaze.job-scheduler :as js]
   [blaze.job.disk-perf :as job-disk-perf]
   [blaze.job.disk-perf.spec]
   [blaze.module :as m]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [java-time.api :as time]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(defn- parameters [{:fhir/keys [type] :as body}]
  (cond
    (nil? body) {:fhir/type :fhir/Parameters}
    (= :fhir/Parameters type) body
    :else (ba/incorrect (format "Expected Parameters resource but was `%s` resource." (name type)))))

(defn- coerce [spec msg]
  (fn [value]
    (let [value (:value value)]
      (if (s/valid? spec value)
        value
        (ba/incorrect msg)))))

(def ^:private param-specs
  "Specs of the optional job parameters. Missing parameters are left out so
  that the job runs with its defaults."
  {"database"
   {:action :copy
    :coerce (coerce ::job-disk-perf/database "Has to be a code.")}
   "file-size"
   {:action :copy
    :coerce (coerce ::job-disk-perf/file-size "Has to be a decimal between 1 MiB and 64 GiB.")}
   "phase-duration"
   {:action :copy
    :coerce (coerce ::job-disk-perf/phase-duration "Has to be a decimal between 0 and 300 seconds.")}
   "max-concurrency"
   {:action :copy
    :coerce (coerce ::job-disk-perf/max-concurrency "Has to be an integer between 1 and 1024.")}})

(defmethod m/pre-init-spec :blaze.fhir.operation/disk-perf [_]
  (s/keys :req-un [:blaze/clock] :opt-un [:blaze/context-path]))

(defmethod ig/init-key :blaze.fhir.operation/disk-perf
  [_ {:keys [clock] :as context}]
  (log/info "Init FHIR $disk-perf operation handler")
  (fn [{:keys [body] :blaze/keys [job-scheduler] :as request}]
    (if-ok [parameters (parameters body)
            params (fu/coerce-params param-specs parameters)]
      (let [authored-on (time/offset-date-time clock)]
        (log/debug "Initiate async response...")
        (do-sync [job (js/create-job job-scheduler (job-disk-perf/job authored-on params))]
          (-> (ring/status 202)
              (handler-util/async-status-location context request job))))
      ac/completed-future)))
