(ns blaze.fhir.operation.disk-perf
  "Main entry point into the $disk-perf operation."
  (:require
   [blaze.anomaly :as ba]
   [blaze.handler.async-job :as async-job]
   [blaze.job.disk-perf :as job-disk-perf]
   [blaze.job.disk-perf.spec]
   [blaze.module :as m]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [taoensso.timbre :as log]))

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
  [_ context]
  (log/info "Init FHIR $disk-perf operation handler")
  (async-job/handler context param-specs job-disk-perf/job))
