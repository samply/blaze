(ns blaze.job.test-util
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.job.util :as job-util]
   [blaze.luid :as luid])
  (:import
   [java.util.concurrent TimeUnit]))

(defn combined-status [{:keys [status] :as job}]
  (if-let [status-reason (job-util/status-reason job)]
    (keyword (type/value status) status-reason)
    (keyword (type/value status))))

(defn- job-id [{{:keys [clock rng-fn]} :context}]
  (luid/luid clock (rng-fn)))

(defn- pull-job*
  [{:blaze.db.admin/keys [node] :as system} job-id status]
  (-> (d/pull node (d/resource-handle (d/db node) "Task" job-id))
      (ac/then-compose-async
       (fn [job]
         (if (= status (combined-status job))
           (ac/completed-future job)
           (pull-job* system job-id status)))
       (ac/delayed-executor 10 TimeUnit/MILLISECONDS))))

(defn pull-job
  "Tries to pull the job with `status` from `system`.

  Waits until `status` is reached or 10 seconds are eclipsed."
  ([{:blaze/keys [job-scheduler] :as system} status]
   (pull-job system (job-id job-scheduler) status))
  ([system job-id status]
   (-> (pull-job* system job-id status)
       (ac/or-timeout! 10 TimeUnit/SECONDS))))

(defn pull-job-history
  [{:blaze/keys [job-scheduler] :blaze.db.admin/keys [node]}]
  (-> (d/pull-many node (d/instance-history (d/db node) "Task" (job-id job-scheduler)))
      (ac/then-apply reverse)))
