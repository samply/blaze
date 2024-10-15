(ns blaze.job.prune
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

(def ^:private ^:const ^long num-index-entries-to-process 100000)

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
  (if index
    (cond-> (add-output job "next-index" (name index))
      type (add-output "next-type" type)
      id (add-output "next-id" id)
      t (add-output "next-t" t))
    (job-util/remove-output job output-system "next-index")))

(defn- increment-job
  [job
   {:keys [num-index-entries-processed num-index-entries-deleted duration next]}]
  (-> (assoc job :statusReason job-util/incremented-status-reason)
      (increment-index-entries-processed num-index-entries-processed)
      (increment-index-entries-deleted num-index-entries-deleted)
      (increment-duration duration)
      (set-next next)))

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
  (some-> (job-util/output-value job output-system "next-t") type/value Long/parseLong))

(defn- next-handle [job]
  (when-let [index (next-index job)]
    (let [type (next-type job)]
      (cond-> {:index index}
        type
        (assoc :type type :id (next-id job) :t (next-t job))))))

(defn- update-job [{:keys [admin-node]} job {:keys [next] :as result}]
  (if next
    (job-util/update-job admin-node job increment-job result)
    (job-util/update-job admin-node job complete-job result)))

(defn- assoc-duration [start result]
  (assoc result :duration (BigDecimal/valueOf (- (System/nanoTime) start) 9)))

(defn- time-future [future]
  (ac/then-apply future (partial assoc-duration (System/nanoTime))))

(defn- prune-fn [main-node t]
  (fn
    ([]
     (-> (ac/supply-async #(d/prune main-node num-index-entries-to-process t))
         (time-future)))
    ([next]
     (-> (ac/supply-async #(d/prune main-node num-index-entries-to-process t next))
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
           (-> (prune next)
               (ac/handle (continuation context prune job))
               (ac/then-compose identity))))))))

(def ^:private missing-t-anom
  (ba/incorrect "Missing T."))

(defn- on-start
  [{:keys [main-node admin-node] :as context} job]
  (if-let [t (t job)]
    (let [total (d/prune-total main-node)
          prune (prune-fn main-node t)]
      (-> (job-util/update-job admin-node job start-job total)
          (ac/then-compose
           (fn [job]
             (-> (prune)
                 (ac/handle (continuation context prune job))
                 (ac/then-compose identity))))))
    (job-util/update-job admin-node job job-util/fail-job missing-t-anom)))

(defn- on-resume
  [{:keys [main-node] :as context} job]
  (let [prune (prune-fn main-node (t job))
        next (next-handle job)]
    (-> (if next (prune next) (prune))
        (ac/handle (continuation context prune job))
        (ac/then-compose identity))))

(defmethod m/pre-init-spec :blaze.job/prune [_]
  (s/keys :req-un [::main-node ::admin-node :blaze/clock]))

(defmethod ig/init-key :blaze.job/prune
  [_ config]
  (reify p/JobHandler
    (-on-start [_ job]
      (on-start config job))
    (-on-resume [_ job]
      (on-resume config job))))

(derive :blaze.job/prune :blaze.job/handler)
