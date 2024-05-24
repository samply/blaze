(ns blaze.job-scheduler
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.async.flow :as flow]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.spec]
   [blaze.fhir.spec.type :as type]
   [blaze.job.util :as job-util]
   [blaze.luid :as luid]
   [blaze.module :as m]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [taoensso.timbre :as log])
  (:import
   [java.util.concurrent Flow$Subscriber]))

(set! *warn-on-reflection* true)

(defmulti on-start
  "Handles the event that a job was started.

  The jobs status will be ready and should be set to in-progress/incremented if
  it can be started.

  The `context` contains the :main-node and the :admin-node.

  Returns a CompletableFuture that will complete with a possibly updated job
  or will complete exceptionally with an anomaly in case of errors."
  {:arglists '([context job])}
  (fn [_ job]
    (job-util/job-type job)))

(defn- failed-msg [action {:keys [id] :as job}]
  (format "Failed to %s job with id `%s` because the implementation for the type `%s` is missing."
          action id (name (job-util/job-type job))))

(defn- failed-fault [action]
  (ba/fault (format "Failed to %s because the implementation is missing." action)))

(defmethod on-start :default
  [{:keys [admin-node]} job]
  (log/warn (failed-msg "start" job))
  (job-util/update-job admin-node job job-util/fail-job (failed-fault "start")))

(defn- pull-job [admin-node id]
  (d/pull admin-node (d/resource-handle (d/db admin-node) "Task" id)))

(defn- fail-job-on-error [admin-node id e]
  (log/error "Error while executing the job with id =" id (::anom/message e))
  (-> (pull-job admin-node id)
      (ac/then-compose-async
       #(job-util/update-job admin-node % job-util/fail-job e))))

(defn- job-completion-handler [{:keys [admin-node]} running-jobs id]
  (fn [_ e]
    (swap! running-jobs dissoc id)
    (if e
      (if (job-util/job-update-failed? e)
        (log/debug "Paused job with id =" id)
        (fail-job-on-error admin-node id e))
      (log/debug "Finished job with id =" id))))

(defn- wrap-error [f context job]
  (try
    (f context job)
    (catch Exception e
      (ac/completed-future (ba/anomaly e)))))

(defn- on-start* [{:keys [context running-jobs]} {:keys [id] :as job}]
  (log/debug "Started job with id =" id)
  (->> (ac/handle (wrap-error on-start context job)
                  (job-completion-handler context running-jobs id))
       (swap! running-jobs assoc id)))

(defmulti on-resume
  "Handles the event that a job was resumed.

  The jobs status will be in-progress/resumed and should be set to
  in-progress/incremented on the next increment.

  Returns a CompletableFuture that will complete with a possibly updated job
  or will complete exceptionally with an anomaly in case of errors."
  {:arglists '([context job])}
  (fn [_ job]
    (job-util/job-type job)))

(defmethod on-resume :default
  [{:keys [admin-node]} job]
  (log/warn (failed-msg "resume" job))
  (job-util/update-job admin-node job job-util/fail-job (failed-fault "resume")))

(defn- on-resume* [{:keys [context running-jobs]} {:keys [id] :as job}]
  (log/debug "Resumed job with id =" id)
  (->> (ac/handle (wrap-error on-resume context job)
                  (job-completion-handler context running-jobs id))
       (swap! running-jobs assoc id)))

(deftype TaskSubscriber [admin-node job-scheduler ^:volatile-mutable subscription]
  Flow$Subscriber
  (onSubscribe [_ s]
    (set! subscription s)
    (flow/request! subscription 1))
  (onNext [_ task-handles]
    (log/debug "Got" (count task-handles) "changed task(s)")
    (run!
     (fn [{:keys [status] :as job}]
       (cond
         (= #fhir/code"ready" status)
         (on-start* job-scheduler job)
         (and (= #fhir/code"in-progress" status) (= "resumed" (job-util/status-reason job)))
         (on-resume* job-scheduler job)))
     @(d/pull-many admin-node task-handles))
    (flow/request! subscription 1))
  (onError [_ _e]
    (flow/cancel! subscription))
  (onComplete [_]))

(defn- luid [{:keys [clock rng-fn]}]
  (luid/luid clock (rng-fn)))

(defn- current-job-number-observation [{:keys [admin-node] :as context} db]
  (if-let [handle (coll/first (d/type-query db "Observation" [["identifier" "job-number"]]))]
    (d/pull admin-node handle)
    (-> (d/transact admin-node
                    [[:create
                      {:fhir/type :fhir/Observation
                       :id (luid context)
                       :identifier [#fhir/Identifier{:value "job-number"}]
                       :value #fhir/integer 0}
                      [["identifier" "job-number"]]]])
        (ac/then-compose (partial current-job-number-observation context)))))

(def ^:private inc-fhir-integer
  (comp type/integer inc type/value))

(defn- job-number-identifier [job-number]
  (type/map->Identifier
   {:use #fhir/code"official"
    :system (type/uri job-util/job-number-url)
    :value (str job-number)}))

(defn- prepare-job [job id job-number]
  (-> (assoc job :id id)
      (update :identifier (fnil conj []) (job-number-identifier job-number))))

(defn create-job
  "Returns a CompletableFuture that will complete with `job` created or will
  complete exceptionally with an anomaly in case of errors."
  {:arglists '([job-scheduler job & other-resources])}
  [{{:keys [admin-node] :as context} :context} job & other-resources]
  (-> (current-job-number-observation context (d/db admin-node))
      (ac/then-compose-async
       (fn [{job-number :value :as obs}]
         (let [id (luid context)]
           (-> (d/transact
                admin-node
                (into
                 [[:put (update obs :value inc-fhir-integer) [:if-match (:blaze.db/t (:blaze.db/tx (meta obs)))]]
                  [:create (prepare-job job id (inc (type/value job-number)))]]
                 (map (fn [resource] [:create resource]))
                 other-resources))
               (ac/then-compose
                (fn [db] (d/pull db (d/resource-handle db "Task" id))))))))))

(defn- hold-job** [job reason]
  (assoc
   job
   :status #fhir/code"on-hold"
   :statusReason reason))

(defn- hold-job* [{:keys [admin-node]} id reason conflict-msg]
  (-> (pull-job admin-node id)
      (ac/then-compose-async
       (fn [{:keys [status] :as job}]
         (condp = status
           #fhir/code"in-progress"
           (job-util/update-job admin-node job hold-job** reason)
           #fhir/code"on-hold"
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
   :status #fhir/code"in-progress"
   :statusReason job-util/resumed-status-reason))

(defn- resume-job* [{:keys [admin-node]} id]
  (-> (pull-job admin-node id)
      (ac/then-compose-async
       (fn [{:keys [status] :as job}]
         (condp = status
           #fhir/code"on-hold"
           (job-util/update-job admin-node job resume-job**)
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
  (s/keys :req-un [::main-node ::admin-node :blaze/clock :blaze/rng-fn]))

(defmethod ig/init-key :blaze/job-scheduler
  [_ {:keys [admin-node] :as context}]
  (log/info "Start job scheduler")
  (let [publisher (d/changed-resources-publisher admin-node "Task")
        job-scheduler {:context context :running-jobs (atom {})}
        subscriber (->TaskSubscriber admin-node job-scheduler nil)]
    (flow/subscribe! publisher subscriber)
    job-scheduler))

(defmethod ig/halt-key! :blaze/job-scheduler
  [_ job-scheduler]
  (log/info "Stopping job scheduler...")
  (shutdown job-scheduler)
  (log/info "Job scheduler was stopped successfully"))
