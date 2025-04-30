(ns blaze.job.prune
  "The prune job calls d/prune in several steps ensuring that the progress can
  be tracked accordingly. Prune jobs can be paused and resumed."
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.job-scheduler.protocols :as p]
   [blaze.job.prune.spec]
   [blaze.job.util :as job-util]
   [blaze.module :as m]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]))

(set! *warn-on-reflection* true)

(def ^:private ^:const ^long default-index-entries-per-step 100000)

(def ^:private parameter-system
  "https://samply.github.io/blaze/fhir/CodeSystem/PruneJobParameter")

(def ^:private output-system
  "https://samply.github.io/blaze/fhir/CodeSystem/PruneJobOutput")

(def ^:private task-output
  (partial job-util/task-output output-system))

(def ^:private initial-duration
  #fhir/Quantity
   {:value #fhir/decimal 0
    :unit #fhir/string"s"
    :system #fhir/uri"http://unitsofmeasure.org"
    :code #fhir/code"s"})

(defn- start-job [job total-index-entries]
  (assoc
   job
   :status #fhir/code"in-progress"
   :statusReason job-util/started-status-reason
   :output
   [(task-output "total-index-entries" (type/unsignedInt total-index-entries))
    (task-output "index-entries-processed" #fhir/unsignedInt 0)
    (task-output "index-entries-deleted" #fhir/unsignedInt 0)
    (task-output "processing-duration" initial-duration)]))

(defn- add-output [job code value]
  (job-util/add-output job output-system code value))

(defn- remove-output [job code]
  (job-util/remove-output job output-system code))

(defn- set-output [job code value]
  (if value
    (add-output job code value)
    (remove-output job code)))

(defn- increment-unsigned-int [value x]
  (type/unsignedInt (+ (type/value value) x)))

(defn- increment-index-entries-processed [job num-index-entries]
  (job-util/update-output-value job output-system "index-entries-processed"
                                increment-unsigned-int num-index-entries))

(defn- increment-index-entries-deleted [job num-index-entries]
  (job-util/update-output-value job output-system "index-entries-deleted"
                                increment-unsigned-int num-index-entries))

(defn- increment-quantity-value [quantity x]
  (update quantity :value #(type/decimal (+ (type/value %) x))))

(defn- increment-duration [job duration]
  (job-util/update-output-value job output-system "processing-duration"
                                increment-quantity-value duration))

(defn- set-next [job {:keys [index type id t]}]
  (-> (set-output job "next-index" (some-> index name type/code))
      (set-output "next-type" (some-> type type/code))
      (set-output "next-id" (some-> id type/id))
      (set-output "next-t" (some-> t type/positiveInt))))

(defn- increment-on-hold-job
  [job
   {:keys [num-index-entries-processed num-index-entries-deleted duration next]}]
  (-> (increment-index-entries-processed job num-index-entries-processed)
      (increment-index-entries-deleted num-index-entries-deleted)
      (increment-duration duration)
      (set-next next)))

(defn- increment-job [job result]
  (-> (assoc job :statusReason job-util/incremented-status-reason)
      (increment-on-hold-job result)))

(defn- complete-job [job result]
  (-> (increment-job job result)
      (assoc :status #fhir/code"completed")
      (dissoc :statusReason)))

(defn- t [job]
  (some-> (job-util/input-value job parameter-system "t") type/value))

(defn- next-index [job]
  (some-> (job-util/output-value job output-system "next-index") type/value keyword))

(defn- next-type [job]
  (some-> (job-util/output-value job output-system "next-type") type/value))

(defn- next-id [job]
  (some-> (job-util/output-value job output-system "next-id") type/value))

(defn- next-t [job]
  (some-> (job-util/output-value job output-system "next-t") type/value))

(defn- next-handle [job]
  (when-let [index (next-index job)]
    (let [type (next-type job)]
      (cond-> {:index index}
        type
        (assoc :type type :id (next-id job) :t (next-t job))))))

(defn- on-hold? [job]
  (= #fhir/code"on-hold" (:status job)))

(defn- update-job [{:keys [admin-node]} job {:keys [next] :as result}]
  (if next
    (-> (job-util/update-job admin-node job increment-job result)
        (ac/exceptionally-compose
         (fn [e]
           (if (job-util/job-update-failed? e)
             (-> (job-util/pull-job admin-node (:id job))
                 (ac/then-compose
                  (fn [job]
                    (if (on-hold? job)
                      (job-util/update-job admin-node job increment-on-hold-job result)
                      (ac/completed-future job)))))
             (ac/completed-future e)))))
    (job-util/update-job admin-node job complete-job result)))

(defn- assoc-duration [start result]
  (assoc result :duration (BigDecimal/valueOf (- (System/nanoTime) start) 9)))

(defn- time-future [future]
  (ac/then-apply future (partial assoc-duration (System/nanoTime))))

(defn- prune-fn [main-node n t]
  (fn
    ([]
     (-> (ac/supply-async #(d/prune main-node n t))
         (time-future)))
    ([next]
     (-> (ac/supply-async #(d/prune main-node n t next))
         (time-future)))))

(defn- continuation
  "Returns a function that takes a prune result (or nil) and an anomaly
  (or nil), updates the job and continues processing if there is more work to
  do."
  [{:keys [admin-node] :as context} prune job]
  (fn [{:keys [next] :as result} anomaly]
    (if anomaly
      (job-util/update-job admin-node job job-util/fail-job anomaly)
      (cond-> (update-job context job result)
        next
        (ac/then-compose
         (fn [job]
           (if (on-hold? job)
             (ac/completed-future job)
             (-> (prune next)
                 (ac/handle (continuation context prune job))
                 (ac/then-compose identity)))))))))

(def ^:private missing-t-anom
  (ba/incorrect "Missing T."))

(defn- on-start
  [{:keys [main-node admin-node index-entries-per-step]
    :or {index-entries-per-step default-index-entries-per-step}
    :as context} job]
  (if-let [t (t job)]
    (let [total (d/prune-total main-node)
          prune (prune-fn main-node index-entries-per-step t)]
      (-> (job-util/update-job admin-node job start-job total)
          (ac/then-compose
           (fn [job]
             (-> (prune)
                 (ac/handle (continuation context prune job))
                 (ac/then-compose identity))))))
    (job-util/update-job admin-node job job-util/fail-job missing-t-anom)))

(defn- on-resume
  [{:keys [main-node index-entries-per-step]
    :or {index-entries-per-step default-index-entries-per-step}
    :as context} job]
  (let [prune (prune-fn main-node index-entries-per-step (t job))
        next (next-handle job)]
    (-> (if next (prune next) (prune))
        (ac/handle (continuation context prune job))
        (ac/then-compose identity))))

(defmethod m/pre-init-spec :blaze.job/prune [_]
  (s/keys :req-un [::main-node ::admin-node :blaze/clock]
          :opt-un [::index-entries-per-step]))

(defmethod ig/init-key :blaze.job/prune
  [_ config]
  (reify p/JobHandler
    (-on-start [_ job]
      (on-start config job))
    (-on-resume [_ job]
      (on-resume config job))))

(derive :blaze.job/prune :blaze.job/handler)
