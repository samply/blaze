(ns blaze.job-scheduler
  (:require
   [blaze.async.comp :as ac]
   [blaze.async.flow :as flow]
   [blaze.db.api :as d]
   [blaze.db.spec]
   [blaze.fhir.spec.type :as type]
   [blaze.job-scheduler.protocols :as p]
   [blaze.scheduler :as sched]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [taoensso.timbre :as log])
  (:import
   [java.util.concurrent Flow$Subscriber]))

(defmulti execute-job
  {:arglists '([node task])}
  (fn [_ {{[{:keys [code]}] :coding} :code}]
    (keyword (type/value code))))

(defn submit
  "Submits `task` for execution in `scheduler` and returns a CompletableFuture
  that will complete with the stored task in case of success or will complete
  exceptionally with an anomaly in case of errors.

  The stored task will have the status `ready` and added metadata."
  [scheduler task]
  (p/-submit scheduler task))

(defn task [db id]
  (d/resource-handle db "Task" id))

(defn tasks-by-status [db status]
  (d/type-query db "Task" [["status" status]]))

(defn- update-tx-op [{{version-id :versionId} :meta :as task}]
  [:put task [:if-match (parse-long (type/value version-id))]])

(defn- execute-job* [node {:keys [id] :as task}]
  (log/debug "execute job with id =" id)
  (let [db @(d/transact node [(update-tx-op (assoc task :status #fhir/code"in-progress"))])
        task (execute-job node @(d/pull node (d/resource-handle db "Task" id)))]
    @(d/transact node [(update-tx-op (assoc task :status #fhir/code"completed"))])))

(deftype TaskSubscriber [node scheduler ^:volatile-mutable subscription]
  Flow$Subscriber
  (onSubscribe [_ s]
    (set! subscription s)
    (flow/request! subscription 1))
  (onNext [_ task-handles]
    (log/debug "got" (count task-handles) "tasks")
    (run!
     (fn [{:keys [status] :as task}]
       (when (= #fhir/code"ready" status)
         (sched/submit scheduler #(execute-job* node task))))
     @(d/pull-many node task-handles))
    (flow/request! subscription 1))
  (onError [_ _e]
    (flow/cancel! subscription))
  (onComplete [_]))

(deftype JobScheduler [node scheduler subscriber]
  p/JobScheduler
  (-submit [_ {:keys [id] :as task}]
    (log/debug "submit new job with id =" id)
    (-> (d/transact node [[:create (assoc task :status #fhir/code"ready")]])
        (ac/then-compose #(d/pull % (d/resource-handle % "Task" id))))))

(defmethod ig/pre-init-spec :blaze/job-scheduler [_]
  (s/keys :req-un [:blaze.db/node]))

(defmethod ig/init-key :blaze/job-scheduler
  [_ {:keys [node scheduler]}]
  (log/info "Start job scheduler")
  (let [publisher (d/subscription-publisher node "Task")
        subscriber (->TaskSubscriber node scheduler nil)]
    (flow/subscribe! publisher subscriber)
    (->JobScheduler node scheduler subscriber)))

(defmethod ig/halt-key! :blaze/job-scheduler
  [_ _scheduler]
  (log/info "Stopping job scheduler..."))
