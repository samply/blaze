(ns blaze.job.disk-perf
  "The disk performance job measures the performance of the volume of one of
  the database directories with an I/O profile similar to the one Blaze's
  RocksDB stores produce.

  Because a benchmark restarted after an interruption can't reuse partial
  results, resuming a disk performance job restarts the whole benchmark. The
  resumed job stays resumed until its first progress update, which happens
  right after the benchmark has started."
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac]
   [blaze.executors :as ex]
   [blaze.fhir.canonical :as canonical]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.util :as fu]
   [blaze.job-scheduler.protocols :as p]
   [blaze.job.disk-perf.engine :as engine]
   [blaze.job.disk-perf.score :as score]
   [blaze.job.disk-perf.spec]
   [blaze.job.util :as job-util]
   [blaze.module :as m]
   [blaze.time :as bt]
   [blaze.util :as u :refer [conj-vec]]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [taoensso.timbre :as log])
  (:import
   [java.math RoundingMode]
   [java.time ZoneOffset]
   [java.util.concurrent TimeUnit]))

(set! *warn-on-reflection* true)

(def ^:private parameter-system (canonical/url "CodeSystem/DiskPerfJobParameter"))

(def ^:private output-system (canonical/url "CodeSystem/DiskPerfJobOutput"))

(def ^:private ^:const ^long default-file-size (* 4 u/gib))

(def ^:private ^:const ^long default-phase-duration-millis 30000)

(def ^:private ^:const ^long default-max-concurrency 32)

(def ^:private ^:const ^long max-concurrency-limit 1024)

(defn- coding [system code]
  (type/coding {:system (type/uri-interned system) :code (type/code code)}))

(defn- quantity [value code]
  (type/quantity
   {:value (type/decimal value)
    :unit (type/string code)
    :system #fhir/uri-interned "http://unitsofmeasure.org"
    :code (type/code code)}))

(defn- task-input [code value]
  {:fhir/type :fhir.Task/input
   :type (type/codeable-concept {:coding [(coding parameter-system code)]})
   :value value})

(defn job
  "Creates a disk performance job resource.

  All parameters are optional. `file-size` is in GiB, `phase-duration` in
  seconds.

  Unlike older job types, `meta.profile` carries only the current canonical
  because the disk performance job profile never existed in the legacy IG
  edition."
  [authored-on {:keys [database file-size phase-duration max-concurrency]}]
  (cond->
   {:fhir/type :fhir/Task
    :meta (type/meta {:profile [(type/canonical (canonical/url "StructureDefinition/DiskPerfJob"))]})
    :status #fhir/code "ready"
    :intent #fhir/code "order"
    :code (job-util/type-codeable-concept "disk-perf" "Measure Disk Performance")
    :authoredOn (type/dateTime authored-on)}
    database
    (update :input conj-vec (task-input "database" (type/code database)))
    file-size
    (update :input conj-vec (task-input "file-size" (quantity file-size "GiBy")))
    phase-duration
    (update :input conj-vec (task-input "phase-duration" (quantity phase-duration "s")))
    max-concurrency
    (update :input conj-vec (task-input "max-concurrency" (type/positiveInt max-concurrency)))))

(defn- database [job]
  (or (:value (job-util/input-value job parameter-system "database")) "index"))

(defn- dir [{:keys [dirs]} job]
  (let [database (database job)]
    (or (get dirs database)
        (ba/incorrect (format "No local storage directory available for the `%s` database." database)))))

(defn- file-size-bytes [job]
  (if-some [file-size (job-util/input-value job parameter-system "file-size")]
    (let [gib-value (-> file-size :value :value)]
      (if (s/valid? ::file-size gib-value)
        (long (* gib-value u/gib))
        (ba/incorrect (format "Invalid `file-size` parameter %s GiB. The file size has to be between 1 MiB and 64 GiB." gib-value))))
    default-file-size))

(defn- phase-duration-millis [job]
  (if-some [phase-duration (job-util/input-value job parameter-system "phase-duration")]
    (let [seconds (-> phase-duration :value :value)]
      (if (s/valid? ::phase-duration seconds)
        (long (* seconds 1000))
        (ba/incorrect (format "Invalid `phase-duration` parameter %s s. The phase duration has to be between 0 and 300 seconds." seconds))))
    default-phase-duration-millis))

(defn- max-concurrency [job]
  (if-some [value (:value (job-util/input-value job parameter-system "max-concurrency"))]
    (if (s/valid? ::max-concurrency value)
      value
      (ba/incorrect (format "Invalid `max-concurrency` parameter %s. The maximum concurrency has to be between 1 and %d." value max-concurrency-limit)))
    default-max-concurrency))

(defn- params [{:keys [block-size]} job]
  (when-ok [file-size (file-size-bytes job)
            phase-duration-millis (phase-duration-millis job)
            max-concurrency (max-concurrency job)]
    {:file-size file-size
     :block-size block-size
     :concurrencies (engine/concurrency-levels max-concurrency)
     :phase-duration-millis phase-duration-millis}))

(def ^:private initial-duration
  #fhir/Quantity
   {:value #fhir/decimal 0M
    :unit #fhir/string "s"
    :system #fhir/uri-interned "http://unitsofmeasure.org"
    :code #fhir/code "s"})

(defn- task-output [code value]
  {:fhir/type :fhir.Task/output
   :type (type/codeable-concept {:coding [(coding output-system code)]})
   :value value})

(defn- add-output [job code value]
  (update job :output conj (task-output code value)))

(defn- start-job [job]
  (assoc
   job
   :status #fhir/code "in-progress"
   :statusReason job-util/started-status-reason
   :output
   [(task-output "current-phase" #fhir/code "seq-write")
    (task-output "phase-progress" #fhir/unsignedInt 0)
    (task-output "processing-duration" initial-duration)]))

(defn- update-progress [job phase percent]
  (-> (assoc job :statusReason job-util/incremented-status-reason)
      (job-util/update-output-value output-system "current-phase"
                                    (fn [_ value] value) (type/code (name phase)))
      (job-util/update-output-value output-system "phase-progress"
                                    (fn [_ value] value) (type/unsignedInt percent))))

(defn- instant [clock]
  (.atOffset (bt/instant clock) ZoneOffset/UTC))

(defn- elapsed [clock job]
  (-> (bt/duration (-> job :meta :lastUpdated :value) (instant clock))
      (bt/as-seconds)))

(defn- progress-update-due?
  "Returns true if the progress of `job` should be persisted, because it's
  resumed or wasn't updated within the last 10 seconds.

  The first progress of a resumed job is persisted immediately, because the
  benchmark starts over, so that the progress of the job from before it was
  paused is outdated."
  [clock job]
  (or (= "resumed" (job-util/status-reason job))
      (< 10 (elapsed clock job))))

(defn- progress-fn
  "Returns the progress function given to the engine.

  Persists the current phase and progress percentage, throttled to one update
  every 10 seconds, except for the first one of a resumed job. Runs
  synchronously on the engine thread so that no update is in flight when the
  engine finishes.

  Sets `update-failed` if an update fails because the job was updated
  concurrently, so that the benchmark stops. Only stopping the job updates it
  while it's running, by cancelling, pausing or the shutdown of the job
  scheduler. Otherwise the job is outdated, because it was dispatched again
  after its execution had ended."
  [{:keys [admin-node clock]} current-job update-failed]
  (fn [phase fraction]
    (let [job @current-job]
      (when (and (not @update-failed) (progress-update-due? clock job))
        (let [result (ba/try-anomaly
                      @(job-util/update-job admin-node job
                                            #(update-progress % phase (int (* 100 (double fraction))))))]
          (if (ba/anomaly? result)
            (when (job-util/job-update-failed? result)
              (vreset! update-failed true))
            (vreset! current-job result)))))))

(defn- increment-quantity-value [quantity x]
  (update quantity :value #(type/decimal (+ (:value %) x))))

(defn- increment-duration [job duration]
  (job-util/update-output-value job output-system "processing-duration"
                                increment-quantity-value duration))

(defn- round [value scale]
  (.setScale (BigDecimal/valueOf (double value)) (int scale) RoundingMode/HALF_UP))

(defn- micros-quantity [nanos]
  (quantity (round (/ (double nanos) 1000.0) 1) "us"))

(def ^:private concurrency-extension-url
  (canonical/url "StructureDefinition/disk-perf-concurrency"))

(defn- concurrency-extension [concurrency]
  (type/extension
   {:url concurrency-extension-url
    :value (type/positiveInt concurrency)}))

(defn- run-output
  "Returns a task output of one run of the random read sweep, marked with the
  concurrency of the run via the disk-perf-concurrency extension."
  [concurrency code value]
  (assoc (task-output code value)
         :extension [(concurrency-extension concurrency)]))

(defn- add-run-outputs
  [job {:keys [concurrency iops bytes-per-second latency-nanos]}]
  (update job :output into
          [(run-output concurrency "read-iops" (quantity (round iops 1) "/s"))
           (run-output concurrency "read-throughput" (quantity (round bytes-per-second 0) "By/s"))
           (run-output concurrency "read-latency-p50" (micros-quantity (:p50 latency-nanos)))
           (run-output concurrency "read-latency-p95" (micros-quantity (:p95 latency-nanos)))
           (run-output concurrency "read-latency-p99" (micros-quantity (:p99 latency-nanos)))
           (run-output concurrency "read-latency-max" (micros-quantity (:max latency-nanos)))]))

(defn- add-rand-read-outputs [job runs]
  (reduce add-run-outputs job runs))

(defn- complete-job [job {:keys [seq-write rand-read fsync]} duration]
  (let [{:keys [direct? runs]} rand-read
        score-value (score/score {:read-runs runs
                                  :seq-write-bytes-per-second (:bytes-per-second seq-write)
                                  :fsync-rate (:rate fsync)})]
    (-> (assoc job :status #fhir/code "completed")
        (dissoc :statusReason :businessStatus)
        (job-util/remove-output output-system "current-phase")
        (job-util/remove-output output-system "phase-progress")
        (increment-duration duration)
        (add-output "seq-write-throughput" (quantity (round (:bytes-per-second seq-write) 0) "By/s"))
        (add-rand-read-outputs runs)
        (add-output "fsync-rate" (quantity (round (:rate fsync) 1) "/s"))
        (add-output "fsync-latency-p50" (micros-quantity (-> fsync :latency-nanos :p50)))
        (add-output "fsync-latency-p95" (micros-quantity (-> fsync :latency-nanos :p95)))
        (add-output "fsync-latency-p99" (micros-quantity (-> fsync :latency-nanos :p99)))
        (add-output "direct-io" (type/boolean direct?))
        (add-output "score" (type/decimal (round score-value 1)))
        (add-output "rating" (type/code (score/rating score-value))))))

(defn- output-concurrency [{:keys [extension]}]
  (some #(when (= concurrency-extension-url (:url %)) (-> % :value :value)) extension))

(defn- run-values
  "Returns a map of run concurrency to the value of the outputs of `job` with
  `code`."
  [job code]
  (into {} (map (juxt output-concurrency :value))
        (job-util/outputs job output-system code)))

(defn- rand-read-parameters [job]
  (let [iops (run-values job "read-iops")
        throughput (run-values job "read-throughput")
        p50 (run-values job "read-latency-p50")
        p95 (run-values job "read-latency-p95")
        p99 (run-values job "read-latency-p99")
        latency-max (run-values job "read-latency-max")]
    (mapv
     (fn [concurrency]
       ["concurrency" (type/positiveInt concurrency)
        "iops" (iops concurrency)
        "throughput" (throughput concurrency)
        "latency-p50" (p50 concurrency)
        "latency-p95" (p95 concurrency)
        "latency-p99" (p99 concurrency)
        "latency-max" (latency-max concurrency)])
     (sort (keys iops)))))

;; The response of the $disk-perf operation is a Parameters resource with one
;; parameter per result output of the job, where each run of the random read
;; sweep becomes one `rand-read` parameter with its values as parts.
(defmethod job-util/response-resource :disk-perf
  [job]
  (let [value #(job-util/output-value job output-system %)]
    (fu/parameters
     "seq-write-throughput" (value "seq-write-throughput")
     "rand-read" (rand-read-parameters job)
     "fsync-rate" (value "fsync-rate")
     "fsync-latency-p50" (value "fsync-latency-p50")
     "fsync-latency-p95" (value "fsync-latency-p95")
     "fsync-latency-p99" (value "fsync-latency-p99")
     "direct-io" (value "direct-io")
     "score" (value "score")
     "rating" (value "rating")
     "processing-duration" (value "processing-duration"))))

(defn- job-duration [start]
  (BigDecimal/valueOf (- (System/nanoTime) (long start)) 9))

(defn- running?
  "Returns true if `job` is still running, because it's in-progress or its
  cancellation was requested, so that its execution has to end it."
  [{:keys [status] :as job}]
  (or (= "in-progress" (:value status)) (job-util/cancellation-requested? job)))

(defn- not-running-msg [{:keys [id status]}]
  (format "The job `%s` with status `%s` can't be updated by its execution."
          id (:value status)))

(defn- update-if
  "Returns a function that applies the job transition `f` to a job satisfying
  `pred` or returns a conflict otherwise.

  The job can be updated while its execution runs. It's put on-hold, if it's
  paused or the job scheduler shuts down. It's cancelled directly, if it's
  cancelled while on-hold. And it has ended already, if it was dispatched again
  after its execution had ended. Its execution must not update it in
  those cases. The update fails with the conflict, which the job scheduler
  treats as a job unable to update itself."
  [pred f]
  (fn [job]
    (if (pred job)
      (f job)
      (ba/conflict (not-running-msg job)))))

(defn- complete! [{:keys [admin-node]} id result start]
  (let [duration (job-duration start)]
    (job-util/update-job-with-retry
     admin-node 5 id
     (update-if
      running?
      (fn [job]
        (if (job-util/cancellation-requested? job)
          (job-util/finish-cancellation job)
          (complete-job job result duration)))))))

(defn- handle-error!
  "Ends the execution of the job with `id` that ended with the error `e`.

  Finishes the cancellation of the job if the benchmark was interrupted,
  because its cancellation was requested. The benchmark is also interrupted if
  the job was put on-hold or a progress update failed, in which case the job
  isn't updated."
  [{:keys [admin-node]} id e]
  (job-util/update-job-with-retry
   admin-node 5 id
   (if (ba/interrupted? e)
     (update-if job-util/cancellation-requested? job-util/finish-cancellation)
     (update-if running? #(job-util/fail-job % e)))))

(defn- cancelled-fn
  "Returns the function given to the engine that stops the benchmark if the
  job with `id` was requested to stop or a progress update failed."
  [running-jobs id update-failed]
  #(when (or (get @running-jobs id) @update-failed) (ba/interrupted)))

(defn- execute
  "Runs the benchmark of `job`, which is in-progress, and updates the job when
  the benchmark ends."
  [{:keys [executor] ::keys [running-jobs] :as context} dir params {:keys [id] :as job}]
  (swap! running-jobs job-util/mark-running id)
  (let [current-job (volatile! job)
        update-failed (volatile! false)
        start (System/nanoTime)]
    (-> (ac/supply-async
         #(engine/run! dir params (progress-fn context current-job update-failed)
                       (cancelled-fn running-jobs id update-failed))
         executor)
        (ac/then-compose #(complete! context id % start))
        (ac/exceptionally-compose #(handle-error! context id %))
        (ac/when-complete (fn [_ _] (swap! running-jobs dissoc id))))))

(defn- with-params
  "Calls `f` with the directory and the parameters of `job` or fails `job` if
  they are invalid."
  [{:keys [admin-node] :as context} job f]
  (if-ok [dir (dir context job)
          params (params context job)]
    (f dir params)
    (partial job-util/update-job admin-node job job-util/fail-job)))

(defn- on-start [{:keys [admin-node] :as context} job]
  (with-params
    context job
    (fn [dir params]
      (-> (job-util/update-job admin-node job start-job)
          (ac/then-compose (partial execute context dir params))))))

(defn- on-resume
  "Restarts the benchmark of the resumed `job`.

  Doesn't update the job before the benchmark runs, so that the job stays
  resumed until its first progress update."
  [context job]
  (with-params context job #(execute context %1 %2 job)))

(defmethod m/pre-init-spec :blaze.job/disk-perf [_]
  (s/keys :req-un [::dirs ::admin-node ::executor ::block-size :blaze/clock]))

(defmethod ig/init-key :blaze.job/disk-perf
  [_ config]
  (log/info "Init disk performance job handler")
  (let [context (assoc config ::running-jobs (atom {}))]
    (reify p/JobHandler
      (-on-start [_ job]
        (on-start context job))
      (-on-resume [_ job]
        (on-resume context job))
      (-on-cancel [_ job]
        (swap! (::running-jobs context) assoc (:id job) :cancelled)
        (ac/completed-future job))
      (-on-pause [_ job]
        (swap! (::running-jobs context) assoc (:id job) :paused))
      (-on-shutdown [_ job]
        (swap! (::running-jobs context) assoc (:id job) :shutdown)))))

(derive :blaze.job/disk-perf :blaze.job/handler)

(defmethod ig/init-key ::executor
  [_ _]
  (log/info "Init disk performance job executor")
  (ex/single-thread-executor "disk-perf-job"))

(defmethod ig/halt-key! ::executor
  [_ executor]
  (log/info "Stopping disk performance job executor...")
  (ex/shutdown! executor)
  (if (ex/await-termination executor 10 TimeUnit/SECONDS)
    (log/info "Disk performance job executor was stopped successfully")
    (log/warn "Got timeout while stopping the disk performance job executor")))
