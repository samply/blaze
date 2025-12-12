(ns blaze.job.test-util
  (:refer-clojure :exclude [str])
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.job.util :as job-util]
   [blaze.luid :as luid]
   [blaze.util :refer [str]])
  (:import
   [java.util.concurrent TimeUnit]))

(defn combined-status [{{status :value} :status :as job}]
  (if-let [status-reason (job-util/status-reason job)]
    (if-let [cancelled-sub (job-util/cancelled-sub-status job)]
      (keyword (str status "." cancelled-sub) status-reason)
      (keyword status status-reason))
    (if-let [cancelled-sub (job-util/cancelled-sub-status job)]
      (keyword status cancelled-sub)
      (keyword status))))

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
  {:arglists '([system status] [system job-id status])}
  ([{:blaze/keys [job-scheduler] :as system} status]
   (pull-job system (job-id job-scheduler) status))
  ([system job-id status]
   (-> (pull-job* system job-id status)
       (ac/or-timeout! 10 TimeUnit/SECONDS))))

(defn pull-job-history
  ([{:blaze/keys [job-scheduler] :as system}]
   (pull-job-history system (job-id job-scheduler)))
  ([{:blaze.db.admin/keys [node]} job-id]
   (-> (d/pull-many node (vec (d/instance-history (d/db node) "Task" job-id)))
       (ac/then-apply reverse))))

(defn pull-other-resource [{:blaze.db.admin/keys [node]} type id]
  (d/pull node (d/resource-handle (d/db node) type id)))
