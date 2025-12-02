(ns blaze.job-scheduler
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.async.flow :as flow]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.spec]
   [blaze.fhir.spec.type :as type]
   [blaze.job-scheduler.protocols :as p]
   [blaze.job.util :as job-util]
   [blaze.module :as m]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [taoensso.timbre :as log])
  (:import
   [java.util.concurrent Flow$Subscriber]))

(set! *warn-on-reflection* true)

(defn- find-handler [{:keys [handlers]} job]
  (get handlers (keyword "blaze.job" (name (job-util/job-type job)))))

(defn- missing-handler-msg [action {:keys [id] :as job}]
  (format "Failed to %s job with id `%s` because the implementation for the type `%s` is missing."
          action id (name (job-util/job-type job))))

(defn- missing-handler-fault [action]
  (ba/fault (format "Failed to %s because the implementation is missing." action)))

(defn- on-missing-handler [{:keys [node]} action job]
  (log/warn (missing-handler-msg action job))
  (job-util/update-job node job job-util/fail-job (missing-handler-fault action)))

(defn- on-start* [context job]
  (if-let [handler (find-handler context job)]
    (p/-on-start handler job)
    (on-missing-handler context "start" job)))

(defn- fail-job-on-error [node id e]
  (log/error (format "Error while executing the job with id `%s`:" id) (::anom/message e))
  (-> (job-util/pull-job node id)
      (ac/then-compose #(job-util/update-job node % job-util/fail-job e))))

(defn- job-completion-handler [{:keys [node]} running-jobs id]
  (fn [{:keys [status]} e]
    (swap! running-jobs dissoc id)
    (if e
      (if (job-util/job-update-failed? e)
        (log/debug "The job with id =" id "was unable to update itself. It may have been paused.")
        (fail-job-on-error node id e))
      (log/debug "The execution of the job with id =" id "ended with status ="
                 (type/value status)))))

(defn- wrap-error [f context job]
  (try
    (f context job)
    (catch Exception e
      (ac/completed-future (ba/anomaly e)))))

(defn- on-start [{:keys [context running-jobs]} {:keys [id] :as job}]
  (log/debug "Started job with id =" id)
  (->> (ac/handle (wrap-error on-start* context job)
                  (job-completion-handler context running-jobs id))
       (swap! running-jobs assoc id)))

(defn- on-resume* [context job]
  (if-let [handler (find-handler context job)]
    (p/-on-resume handler job)
    (on-missing-handler context "resume" job)))

(defn- on-resume [{:keys [context running-jobs]} {:keys [id] :as job}]
  (log/debug "Resumed job with id =" id)
  (->> (ac/handle (wrap-error on-resume* context job)
                  (job-completion-handler context running-jobs id))
       (swap! running-jobs assoc id)))

(defn- on-cancel* [context job]
  (if-let [handler (find-handler context job)]
    (p/-on-cancel handler job)
    (on-missing-handler context "cancel" job)))

(defn- job-cancellation-completion-handler [running-jobs id]
  (fn [_ _]
    (swap! running-jobs dissoc id)
    (log/debug (format "Finished requesting cancellation of job with id `%s`." id))))

(defn- on-cancel [{:keys [context running-jobs]} {:keys [id] :as job}]
  (log/debug (format "Request cancellation of job with id `%s`." id))
  (->> (ac/handle (wrap-error on-cancel* context job)
                  (job-cancellation-completion-handler running-jobs id))
       (swap! running-jobs assoc id)))

(deftype TaskSubscriber [node job-scheduler ^:volatile-mutable subscription]
  Flow$Subscriber
  (onSubscribe [_ s]
    (set! subscription s)
    (flow/request! subscription 1))
  (onNext [_ task-handles]
    (log/trace "Got" (count task-handles) "changed task(s)")
    (run!
     (fn [{:keys [status] :as job}]
       (cond
         (= #fhir/code "ready" status)
         (on-start job-scheduler job)

         (and (= #fhir/code "in-progress" status)
              (= "resumed" (job-util/status-reason job)))

         (on-resume job-scheduler job)

         (and (= #fhir/code "cancelled" status)
              (= "requested" (job-util/cancelled-sub-status job)))
         (on-cancel job-scheduler job)))
     @(d/pull-many node task-handles))
    (flow/request! subscription 1))
  (onError [_ e]
    (log/fatal "Job scheduler failed. Please restart Blaze. Cause:" (ex-message e))
    (flow/cancel! subscription))
  (onComplete [_]))

(defn- current-job-number-observation [{:keys [node] :as context} db]
  (if-let [handle (coll/first (d/type-query db "Observation" [["identifier" "job-number"]]))]
    (d/pull node handle)
    (-> (d/transact node
                    [[:create
                      {:fhir/type :fhir/Observation
                       :id (m/luid context)
                       :identifier [#fhir/Identifier{:value #fhir/string "job-number"}]
                       :value #fhir/integer 0}
                      [["identifier" "job-number"]]]])
        (ac/then-compose (partial current-job-number-observation context)))))

(def ^:private inc-fhir-integer
  (comp type/integer inc type/value))

(defn- job-number-identifier [job-number]
  (type/identifier
   {:use #fhir/code "official"
    :system (type/uri job-util/job-number-url)
    :value (type/string (str job-number))}))

(defn- prepare-job [job id job-number]
  (-> (assoc job :id id)
      (update :identifier (fnil conj []) (job-number-identifier job-number))))

(defn create-job
  "Returns a CompletableFuture that will complete with `job` created or will
  complete exceptionally with an anomaly in case of errors."
  {:arglists '([job-scheduler job & other-resources])}
  [{{:keys [node] :as context} :context} job & other-resources]
  (-> (current-job-number-observation context (d/db node))
      (ac/then-compose
       (fn [{job-number :value :as obs}]
         (let [id (m/luid context)]
           (-> (d/transact
                node
                (into
                 [[:put (update obs :value inc-fhir-integer) [:if-match (:blaze.db/t (:blaze.db/tx (meta obs)))]]
                  [:create (prepare-job job id (inc (type/value job-number)))]]
                 (map (fn [resource] [:create resource]))
                 other-resources))
               (ac/then-compose
                (fn [db]
                  (log/debug "Created new job with id =" id)
                  (d/pull db (d/resource-handle db "Task" id))))))))))

(defn- cancel-job* [job]
  (-> (assoc
       job
       :status #fhir/code "cancelled"
       :businessStatus job-util/cancellation-requested-sub-status)
      (dissoc :statusReason)))

(defn- cancel-conflict-msg [{:keys [id status]}]
  (format "Can't cancel job `%s` because it's status is `%s`."
          id (type/value status)))

(defn cancel-job
  "Returns a CompletableFuture that will complete with a possibly cancelled job
  or will complete exceptionally with an anomaly in case of errors.

  In case the status of the job is one of `completed`, `failed` or `cancelled`
  the job will not be cancelled and the anomaly will have the category conflict
  and contain the status under the key :job/status."
  {:arglists '([job-scheduler id])}
  [{{:keys [node]} :context} id]
  (log/debug "Try to cancel job with id =" id)
  (-> (job-util/pull-job node id)
      (ac/then-compose
       (fn [{:keys [status] :as job}]
         (if-not (#{#fhir/code "completed" #fhir/code "failed" #fhir/code "cancelled"} status)
           (job-util/update-job node job cancel-job*)
           (ac/completed-future
            (ba/conflict (cancel-conflict-msg job) :job/status (type/value status))))))))

(defn- hold-job** [job reason]
  (assoc
   job
   :status #fhir/code "on-hold"
   :statusReason reason))

(defn- hold-job* [{:keys [node]} id reason conflict-msg]
  (-> (job-util/pull-job node id)
      (ac/then-compose
       (fn [{:keys [status] :as job}]
         (condp = status
           #fhir/code "in-progress"
           (job-util/update-job node job hold-job** reason)
           #fhir/code "on-hold"
           (ac/completed-future job)
           (ac/completed-future (ba/conflict (conflict-msg job))))))))

(defn- pause-conflict-msg [{:keys [id status]}]
  (format "Can't pause job `%s` because it isn't in-progress. It's status is `%s`."
          id (type/value status)))

(defn pause-job
  "Returns a CompletableFuture that will complete with a possibly paused job
  or will complete exceptionally with an anomaly in case of errors."
  {:arglists '([job-scheduler id])}
  [{:keys [context]} id]
  (log/debug "Try to pause job with id =" id)
  (hold-job* context id job-util/paused-status-reason pause-conflict-msg))

(defn- resume-conflict-msg [{:keys [id status]}]
  (format "Can't resume job `%s` because it isn't on-hold. It's status is `%s`."
          id (type/value status)))

(defn- resume-job** [job]
  (assoc
   job
   :status #fhir/code "in-progress"
   :statusReason job-util/resumed-status-reason))

(defn- resume-job* [{:keys [node]} id]
  (-> (job-util/pull-job node id)
      (ac/then-compose
       (fn [{:keys [status] :as job}]
         (condp = status
           #fhir/code "on-hold"
           (job-util/update-job node job resume-job**)
           (ac/completed-future (ba/conflict (resume-conflict-msg job))))))))

(defn resume-job
  "Returns a CompletableFuture that will complete with a possibly resumed job
  or will complete exceptionally with an anomaly in case of errors."
  {:arglists '([job-scheduler id])}
  [{:keys [context]} id]
  (log/debug "Try to resume job with id =" id)
  (resume-job* context id))

(defn- shutdown-conflict-msg [{:keys [id status]}]
  (format "Can't put job `%s` on hold during shutdown because it isn't in-progress. It's status is `%s`."
          id (type/value status)))

(defn- shutdown [{:keys [context running-jobs]}]
  @(ac/all-of (mapv
               #(-> (hold-job* context % job-util/orderly-shut-down-status-reason
                               shutdown-conflict-msg)
                    (ac/exceptionally
                     (fn [{::anom/keys [message]}] (log/warn message))))
               (keys @running-jobs)))
  (some-> (vals @running-jobs) ac/all-of ac/join))

(defmethod m/pre-init-spec :blaze/job-scheduler [_]
  (s/keys :req-un [:blaze.db/node :blaze/clock :blaze/rng-fn]
          :opt-un [::handlers]))

(defmethod ig/init-key :blaze/job-scheduler
  [_ {:keys [node] :as config}]
  (log/info "Start job scheduler")
  (let [publisher (d/changed-resources-publisher node "Task")
        job-scheduler {:context config :running-jobs (atom {})}
        subscriber (->TaskSubscriber node job-scheduler nil)]
    (flow/subscribe! publisher subscriber)
    job-scheduler))

(defmethod ig/halt-key! :blaze/job-scheduler
  [_ job-scheduler]
  (log/info "Stopping job scheduler...")
  (shutdown job-scheduler)
  (log/info "Job scheduler was stopped successfully"))
