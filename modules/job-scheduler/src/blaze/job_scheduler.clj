(ns blaze.job-scheduler
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.async.flow :as flow]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.spec]
   [blaze.fhir.canonical :as canonical]
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
   [java.util.concurrent Flow$Subscriber TimeUnit]))

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

(defn- job-completion-handler [{:keys [node]} id]
  (fn [{:keys [status]} e]
    (if e
      (if (job-util/job-update-failed? e)
        (log/debug "The job with id =" id "was unable to update itself. It may have been paused.")
        (fail-job-on-error node id e))
      (log/debug "The execution of the job with id =" id "ended with status ="
                 (:value status)))))

(defn- wrap-error
  "Calls `f` with `context` and `job` and returns its future, or a future
  completed with an anomaly if `f` throws.

  Catches every Throwable, also errors like the AbstractMethodError of a job
  handler not implementing a method, because the job is already reserved as
  running and its execution has to end."
  [f context job]
  (try
    (f context job)
    (catch Throwable e
      (ac/completed-future (ba/anomaly e)))))

(defn- reserve-job
  "Returns `state` with the job with `id` added to its running jobs with
  `tracked`, unless the job scheduler shuts down or the job is already
  running, so that a job is never executed twice at the same time."
  [{:keys [shutting-down? running-jobs] :as state} id tracked]
  (cond-> state
    (not (or shutting-down? (contains? running-jobs id)))
    (assoc-in [:running-jobs id] tracked)))

(defn- remove-job
  "Returns `state` without the job with `id` in its running jobs."
  [state id]
  (update state :running-jobs dissoc id))

(defn- shut-down
  "Returns `state` in which the job scheduler shuts down, so that no jobs can
  be reserved anymore."
  [state]
  (assoc state :shutting-down? true))

(defn- shutdown-ignore-msg [action {:keys [id]}]
  (format "Ignore %s of job with id `%s`, because the job scheduler shuts down."
          action id))

(defn- running-ignore-msg [action {:keys [id]}]
  (format "Ignore %s of job with id `%s`, because the job is already running."
          action id))

(defn- outdated-ignore-msg [action id]
  (format "Ignore %s of job with id `%s`, because the job has changed in the meantime."
          action id))

(defn- pull-error-msg [action id]
  (format "Failed to pull the job with id `%s` for its %s:" id action))

(defn- execute-current-job
  "Returns a CompletableFuture that will complete after `f` was called with the
  current version of the job with `id` and its future completed, or will
  complete immediately if that version isn't `pending?` anymore.

  The job a dispatch starts from can be outdated, because the scan on start and
  the subscription can both see the same version of a job, and the subscription
  can still deliver versions committed shortly before it was created. So its
  execution can have ended already. Because the job is reserved as running
  before it is pulled, no other execution can start from the version pulled."
  [{:keys [node] :as context} action pending? f id]
  (-> (job-util/pull-job node id)
      (ac/then-compose
       (fn [job]
         (if (pending? job)
           (do (log/debug (format "Execute %s of job with id `%s`." action id))
               (-> (wrap-error f context job)
                   (ac/handle (job-completion-handler context id))))
           (do (log/debug (outdated-ignore-msg action id))
               (ac/completed-future nil)))))
      (ac/exceptionally
       #(log/error (pull-error-msg action id) (::anom/message %)))))

(defn- execute-job
  "Executes `job` by calling `f`, which calls the job handler, unless the job
  scheduler shuts down.

  Reserves the job as running in `state` before its handler is called, so that
  a shutdown either sees the job or refuses the reservation. Doesn't execute a
  job that is already running. Calls `f` with the current version of the job
  only if it is still `pending?`, so that a job isn't executed again from an
  outdated version after its execution ended. The running job has a future of
  its own, that completes after the job was removed from `state` again, when
  the future returned by `f` completes."
  [{:keys [context state]} action pending? f {:keys [id] :as job}]
  (let [tracked (ac/future)
        {:keys [shutting-down? running-jobs]} (swap! state reserve-job id tracked)]
    (cond
      shutting-down?
      (log/debug (shutdown-ignore-msg action job))

      (not (identical? tracked (get running-jobs id)))
      (log/debug (running-ignore-msg action job))

      :else
      (-> (execute-current-job context action pending? f id)
          (ac/when-complete
           (fn [_ _]
             (swap! state remove-job id)
             (ac/complete! tracked nil)))))))

(defn- status-value [{{:keys [value]} :status}]
  value)

(defn- ready? [job]
  (= "ready" (status-value job)))

(defn- resumed? [job]
  (and (= "in-progress" (status-value job))
       (= "resumed" (job-util/status-reason job))))

(defn- cancellation-requested? [job]
  (and (= "cancelled" (status-value job))
       (= "requested" (job-util/cancelled-sub-status job))))

(defn- on-start [job-scheduler job]
  (execute-job job-scheduler "start" ready? on-start* job))

(defn- on-resume* [context job]
  (if-let [handler (find-handler context job)]
    (p/-on-resume handler job)
    (on-missing-handler context "resume" job)))

(defn- on-resume [job-scheduler job]
  (execute-job job-scheduler "resume" resumed? on-resume* job))

(defn- on-cancel* [context job]
  (if-let [handler (find-handler context job)]
    (p/-on-cancel handler job)
    (on-missing-handler context "cancel" job)))

(defn- job-cancellation-completion-handler [id]
  (fn [_ _]
    (log/debug (format "Finished requesting cancellation of job with id `%s`." id))))

(defn- on-cancel
  "Requests the cancellation of `job` from its handler.

  Doesn't track the request in the running jobs, because the job may still be
  running until it notices the cancellation. Its execution stays tracked by the
  future returned when it was started or resumed. Requests cancellations also
  while the job scheduler shuts down, because a cancelled job isn't put
  on-hold, so only its cancellation can stop it."
  [{:keys [context]} {:keys [id] :as job}]
  (log/debug (format "Request cancellation of job with id `%s`." id))
  (-> (wrap-error on-cancel* context job)
      (ac/handle (job-cancellation-completion-handler id))))

(defn- dispatch-job
  "Starts, resumes or requests the cancellation of `job` according to its
  status."
  [job-scheduler job]
  (cond
    (ready? job) (on-start job-scheduler job)
    (resumed? job) (on-resume job-scheduler job)
    (cancellation-requested? job) (on-cancel job-scheduler job)))

(deftype TaskSubscriber [node job-scheduler ^:volatile-mutable subscription]
  Flow$Subscriber
  (onSubscribe [_ s]
    (set! subscription s)
    (flow/request! subscription 1))
  (onNext [_ task-handles]
    (log/trace "Got" (count task-handles) "changed task(s)")
    (run! (partial dispatch-job job-scheduler) @(d/pull-many node task-handles))
    (flow/request! subscription 1))
  (onError [_ e]
    (log/fatal "Job scheduler failed. Please restart Blaze. Cause:" (ex-message e))
    (flow/cancel! subscription))
  (onComplete [_]))

(defn- dispatch-pending-jobs-error-handler [{::anom/keys [message]}]
  (log/error "Failed to dispatch the pending jobs on start of the job scheduler:"
             message))

(defn- dispatch-pending-jobs
  "Dispatches the jobs of `node` that are pending, because they became ready
  or were resumed while no job scheduler was listening, like the
  `TaskSubscriber` does.

  Has to be called after subscribing to Task changes, so that no change falls
  into a gap between the subscription and the database value pulled from. A
  job that is seen by both is executed only once, because a job that is already
  running isn't executed again and a job whose execution ended isn't pending
  anymore.

  Returns a CompletableFuture that will complete after all pending jobs were
  dispatched."
  [node job-scheduler]
  (-> (d/type-query (d/db node) "Task" [["status" "ready" "in-progress"]])
      (ac/then-compose #(d/pull-many node (vec %)))
      (ac/then-apply #(run! (partial dispatch-job job-scheduler) %))
      (ac/exceptionally dispatch-pending-jobs-error-handler)))

(defn- current-job-number-observation [{:keys [node] :as context} db]
  (-> (d/type-query db "Observation" [["identifier" "job-number"]])
      (ac/then-compose
       (fn [handles]
         (if-let [handle (coll/first handles)]
           (d/pull node handle)
           (-> (d/transact node
                           [[:create
                             {:fhir/type :fhir/Observation
                              :id (m/luid context)
                              :identifier [#fhir/Identifier{:value #fhir/string "job-number"}]
                              :value #fhir/integer 0}
                             [["identifier" "job-number"]]]])
               (ac/then-compose (partial current-job-number-observation context))))))))

(def ^:private inc-fhir-integer
  (comp type/integer inc :value))

(defn- job-number-identifiers [job-number]
  (let [value (type/string (str job-number))]
    (mapv
     (fn [system]
       (type/identifier
        {:use #fhir/code "official"
         :system (type/uri-interned system)
         :value value}))
     (canonical/urls "sid/JobNumber"))))

(defn- prepare-job [job id job-number]
  (-> (assoc job :id id)
      (update :identifier (fnil into []) (job-number-identifiers job-number))))

(defn- create-job* [{:keys [node] :as context} job other-resources]
  (-> (current-job-number-observation context (d/db node))
      (ac/then-compose
       (fn [{job-number :value :as obs}]
         (let [id (m/luid context)]
           (-> (d/transact
                node
                (into
                 [[:put (update obs :value inc-fhir-integer) [:if-match (:blaze.db/t (:blaze.db/tx (meta obs)))]]
                  [:create (prepare-job job id (inc (:value job-number)))]]
                 (map (fn [resource] [:create resource]))
                 other-resources))
               (ac/then-compose
                (fn [db]
                  (log/debug "Created new job with id =" id)
                  (d/pull db (d/resource-handle db "Task" id))))))))))

(defn- concurrent-inc?
  "Tests whether `anomaly` is the result of a concurrent increment of the job
  number."
  {:arglists '([anomaly])}
  [{:blaze.db/keys [tx-cmd] :as anomaly}]
  (and (ba/conflict? anomaly) (= "put" (:op tx-cmd))
       (some? (:if-match tx-cmd))))

(defn create-job
  "Returns a CompletableFuture that will complete with `job` created or will
  complete exceptionally with an anomaly in case of errors.

  Retries as long as the job number, used as atomic counter, is incremented
  concurrently."
  {:arglists '([job-scheduler job & other-resources])}
  [{:keys [context]} job & other-resources]
  (ac/retry2 #(create-job* context job other-resources) concurrent-inc?))

(defn- cancel-conflict-msg [{:keys [id status]}]
  (format "Can't cancel job `%s` because it's status is `%s`." id (:value status)))

(defn- cancel-conflict-anom [{:keys [status] :as job}]
  (ba/conflict (cancel-conflict-msg job) :job/status (:value status)))

(defn- cancel-job* [{:keys [status] :as job}]
  (if-not (#{"completed" "failed" "cancelled"} (:value status))
    (-> (assoc
         job
         :status #fhir/code "cancelled"
         :businessStatus job-util/cancellation-requested-sub-status)
        (dissoc :statusReason))
    (cancel-conflict-anom job)))

(defn cancel-job
  "Returns a CompletableFuture that will complete with a possibly cancelled job
  or will complete exceptionally with an anomaly in case of errors.

  In case the status of the job is one of `completed`, `failed` or `cancelled`
  the job will not be cancelled and the anomaly will have the category conflict
  and contain the status under the key :job/status.

  Retries the cancellation up to 5 times on concurrent update problems."
  {:arglists '([job-scheduler id])}
  [{{:keys [node]} :context} id]
  (log/debug "Try to cancel job with id =" id)
  (job-util/update-job-with-retry node 5 id cancel-job*))

(defn- hold-job** [job reason]
  (assoc
   job
   :status #fhir/code "on-hold"
   :statusReason reason))

(defn- hold-job* [{:keys [node]} id reason conflict-msg]
  (-> (job-util/pull-job node id)
      (ac/then-compose
       (fn [{:keys [status] :as job}]
         (condp = (:value status)
           "in-progress" (job-util/update-job node job hold-job** reason)
           "on-hold" (ac/completed-future job)
           (ac/completed-future (ba/conflict (conflict-msg job))))))))

(defn- pause-conflict-msg [{:keys [id status]}]
  (format "Can't pause job `%s` because it isn't in-progress. It's status is `%s`."
          id (:value status)))

(defn- on-pause
  "Signals the handler of the paused `job` if the job is running, so that its
  execution stops as soon as possible and the job can be resumed."
  [context state {:keys [id] :as job}]
  (when (contains? (:running-jobs @state) id)
    (when-let [handler (find-handler context job)]
      (p/-on-pause handler job))))

(defn pause-job
  "Returns a CompletableFuture that will complete with a possibly paused job
  or will complete exceptionally with an anomaly in case of errors.

  Signals the handler of a running job, so that its execution stops."
  {:arglists '([job-scheduler id])}
  [{:keys [context state]} id]
  (log/debug "Try to pause job with id =" id)
  (do-sync [job (hold-job* context id job-util/paused-status-reason
                           pause-conflict-msg)]
    (on-pause context state job)
    job))

(defn- resume-conflict-msg [{:keys [id status]}]
  (format "Can't resume job `%s` because it isn't on-hold. It's status is `%s`."
          id (:value status)))

(defn- resume-running-conflict-msg [id]
  (format "Can't resume job `%s` because its previous execution is still running."
          id))

(defn- resume-job** [job]
  (assoc
   job
   :status #fhir/code "in-progress"
   :statusReason job-util/resumed-status-reason))

(defn- resume-job* [{:keys [node]} state id]
  (-> (job-util/pull-job node id)
      (ac/then-compose
       (fn [{:keys [status] :as job}]
         (cond
           (not= "on-hold" (:value status))
           (ac/completed-future (ba/conflict (resume-conflict-msg job)))

           (contains? (:running-jobs @state) id)
           (ac/completed-future (ba/conflict (resume-running-conflict-msg id)))

           :else
           (job-util/update-job node job resume-job**))))))

(defn resume-job
  "Returns a CompletableFuture that will complete with a possibly resumed job
  or will complete exceptionally with an anomaly in case of errors.

  A job that was paused can't be resumed until its previous execution has
  ended, because a job is never executed twice at the same time."
  {:arglists '([job-scheduler id])}
  [{:keys [context state]} id]
  (log/debug "Try to resume job with id =" id)
  (resume-job* context state id))

(defn- shutdown-conflict-msg [{:keys [id status]}]
  (format "Can't put job `%s` on hold during shutdown because it isn't in-progress. It's status is `%s`."
          id (:value status)))

(defn- pull-again-later
  "Returns a CompletableFuture that will complete with the result of calling
  `f`, which pulls a job again, after a short delay."
  [f]
  (-> (ac/completed-future nil)
      (ac/then-compose-async
       (fn [_] (f))
       (ac/delayed-executor 10 TimeUnit/MILLISECONDS))))

(defn- hold-on-shutdown
  "Returns a CompletableFuture that will complete with the job with `id` put
  on-hold with the reason `orderly-shutdown` or will complete exceptionally
  with an anomaly if the job isn't running anymore. A job that is on-hold
  already, because it was paused, is returned as it is, so that it stays
  paused.

  A job that is still `ready` while it is in the running jobs of `state` is
  about to be put in-progress by its handler, so it is pulled again until it
  isn't ready anymore. Takes the `state` atom, because the job can leave the
  running jobs while it is pulled again. A hold that fails, because the job was
  updated concurrently, is retried as well."
  [{:keys [node] :as context} state id]
  (-> (job-util/pull-job node id)
      (ac/then-compose
       (fn [{:keys [status] :as job}]
         (condp = (:value status)
           "ready"
           (if (contains? (:running-jobs @state) id)
             (pull-again-later #(hold-on-shutdown context state id))
             (ac/completed-future (ba/conflict (shutdown-conflict-msg job))))

           "in-progress"
           (-> (job-util/update-job node job hold-job**
                                    job-util/orderly-shut-down-status-reason)
               (ac/exceptionally-compose
                #(if (job-util/job-update-failed? %)
                   (hold-on-shutdown context state id)
                   (ac/completed-future %))))

           "on-hold" (ac/completed-future job)

           (ac/completed-future (ba/conflict (shutdown-conflict-msg job))))))))

(defn- on-shutdown
  "Signals the handler of the on-hold `job` that the job scheduler shuts down,
  so that the job stops as soon as possible."
  [context job]
  (when-let [handler (find-handler context job)]
    (p/-on-shutdown handler job)))

(defn- shutdown
  "Stops starting and resuming jobs, puts all running jobs on-hold, signals
  their handlers to stop them and waits until they have stopped.

  Takes the running jobs in the same atomic step that stops jobs from being
  reserved, so that every job executing is among them."
  [{:keys [context state]}]
  (let [{:keys [running-jobs]} (swap! state shut-down)]
    @(ac/all-of (mapv
                 #(-> (hold-on-shutdown context state %)
                      (ac/then-apply (partial on-shutdown context))
                      (ac/exceptionally
                       (fn [{::anom/keys [message]}] (log/warn message))))
                 (keys running-jobs)))
    (some-> (vals running-jobs) ac/all-of ac/join)))

(defmethod m/pre-init-spec :blaze/job-scheduler [_]
  (s/keys :req-un [:blaze.db/node :blaze/clock :blaze/rng-fn]
          :opt-un [::handlers]))

(defmethod ig/init-key :blaze/job-scheduler
  [_ {:keys [node] :as config}]
  (log/info "Start job scheduler")
  (let [job-scheduler {:context config
                       :state (atom {:shutting-down? false :running-jobs {}})}]
    (d/subscribe-changes! node "Task" "job-scheduler"
                          (->TaskSubscriber node job-scheduler nil))
    (dispatch-pending-jobs node job-scheduler)
    job-scheduler))

(defmethod ig/halt-key! :blaze/job-scheduler
  [_ job-scheduler]
  (log/info "Stopping job scheduler...")
  (shutdown job-scheduler)
  (log/info "Job scheduler was stopped successfully"))
