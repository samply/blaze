(ns blaze.job.util
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.job-scheduler :as-alias js]
   [cognitect.anomalies :as anom]))

(def ^:const job-number-url
  "https://samply.github.io/blaze/fhir/sid/JobNumber")

(def ^:const type-url
  "https://samply.github.io/blaze/fhir/CodeSystem/JobType")

(def ^:const status-reason-url
  "https://samply.github.io/blaze/fhir/CodeSystem/JobStatusReason")

(def ^:const cancelled-sub-status-url
  "https://samply.github.io/blaze/fhir/CodeSystem/JobCancelledSubStatus")

(def ^:private output-system
  "https://samply.github.io/blaze/fhir/CodeSystem/JobOutput")

(defn- mk-status-reason [reason]
  (type/codeable-reference
   {:concept
    (type/codeable-concept
     {:coding
      [(type/coding
        {:system (type/uri status-reason-url)
         :code (type/code reason)})]})}))

(defn- mk-sub-status [system-url code]
  (type/codeable-concept
   {:coding
    [(type/coding
      {:system (type/uri system-url)
       :code (type/code code)})]}))

(def orderly-shut-down-status-reason (mk-status-reason "orderly-shutdown"))
(def paused-status-reason (mk-status-reason "paused"))
(def resumed-status-reason (mk-status-reason "resumed"))
(def incremented-status-reason (mk-status-reason "incremented"))
(def started-status-reason (mk-status-reason "started"))
(def cancellation-requested-sub-status (mk-sub-status cancelled-sub-status-url "requested"))
(def cancellation-finished-sub-status (mk-sub-status cancelled-sub-status-url "finished"))

(defn job-number
  {:arglists '([job])}
  [{:keys [identifier]}]
  (some
   #(when (= job-number-url (type/value (:system %))) (type/value (:value %)))
   identifier))

(defn code-value
  "Returns the value of the code of the coding with `system` or nil if not
  found."
  {:arglists '([system codeable-concept])}
  [system {:keys [coding]}]
  (some #(when (= system (type/value (:system %))) (type/value (:code %))) coding))

(defn job-type
  "Returns the type of `job` as keyword."
  {:arglists '([job])}
  [{:keys [code]}]
  (keyword (code-value type-url code)))

(defn status-reason
  "Returns the status reason of `job`."
  {:arglists '([job])}
  [{{:keys [concept]} :statusReason}]
  (code-value status-reason-url concept))

(defn cancelled-sub-status
  "Returns the business/sub status with system `.../JobCancelledSubStatus` of
  `job`."
  {:arglists '([job])}
  [{:keys [businessStatus]}]
  (code-value cancelled-sub-status-url businessStatus))

(defn- io-pred [system code]
  #(when (= code (code-value system (:type %))) (:value %)))

(defn input-value
  "Returns the value of the input of `job` with a type containing a coding with
  `system` and `code` or nil if not found."
  {:arglists '([job system code])}
  [{:keys [input]} system code]
  (some (io-pred system code) input))

(defn output-value
  "Returns the value of the output of `job` with a type containing a coding
  with `system` (optional) and `code` or nil if not found."
  {:arglists '([job code] [job system code])}
  ([job code]
   (output-value job output-system code))
  ([{:keys [output]} system code]
   (some (io-pred system code) output)))

(defn error-category
  "Returns the error category of `job` in case it failed and an error category
  is available."
  [job]
  (some->> (output-value job "error-category") (keyword "cognitect.anomalies")))

(defn error-msg
  "Returns the error message of `job` in case it failed."
  [job]
  (output-value job "error"))

(defn error
  "Returns the error as anomaly of `job` in case it failed."
  [job]
  (when-let [msg (error-msg job)]
    #::anom{:category (or (error-category job) ::anom/fault) :message msg}))

(defn- update-output-value* [system code f x output]
  (cond-> output
    (= code (code-value system (:type output)))
    (update :value f x)))

(defn update-output-value
  [job system code f x]
  (update job :output #(mapv (partial update-output-value* system code f x) %)))

(defn task-output [system code value]
  {:fhir/type :fhir.Task/output
   :type (type/codeable-concept
          {:coding
           [(type/coding
             {:system (type/uri system)
              :code (type/code code)})]})
   :value value})

(defn- remove-output* [system code]
  (remove #(= code (code-value system (:type %)))))

(defn remove-output [job system code]
  (update job :output (partial into [] (remove-output* system code))))

(defn- conj-output-value* [outputs system code value]
  (conj (into [] (remove-output* system code) outputs)
        (task-output system code value)))

(defn add-output
  ([job code value]
   (add-output job output-system code value))
  ([job system code value]
   (update job :output conj-output-value* system code value)))

(defn- update-tx-op [{{version-id :versionId} :meta :as job}]
  [:put job [:if-match (parse-long (type/value version-id))]])

(defn- tx-ops [job other-resources]
  (into [(update-tx-op job)] (map (partial vector :create)) other-resources))

(defn- tag-update-error [e]
  (assoc e ::js/action :update-job))

(defn pull-job
  "Functions applied after the returned future are executed on the common
  ForkJoinPool."
  ([node id]
   (pull-job node (d/db node) id))
  ([node db id]
   (d/pull node (d/resource-handle db "Task" id))))

(defn- then-pull-job [stage node id]
  (ac/then-compose stage #(pull-job node % id)))

(defn update-job+
  "Submits a transaction that updates `job` to a state as result of applying
  `f` to `job` and any supplied args. In addition to `job`, `other-resources`
  are also stored.

  Returns a CompletableFuture that will complete with the job after the
  transaction in case of success or will complete exceptionally with an anomaly
  in case of a transaction error or other errors."
  {:arglists
   '([node job other-resources f]
     [node job other-resources f x]
     [node job other-resources f x y])}
  ([node {:keys [id] :as job} other-resources f]
   (-> (d/transact node (tx-ops (f job) other-resources))
       (then-pull-job node id)
       (ac/exceptionally tag-update-error)))
  ([node {:keys [id] :as job} other-resources f x]
   (-> (d/transact node (tx-ops (f job x) other-resources))
       (then-pull-job node id)
       (ac/exceptionally tag-update-error)))
  ([node {:keys [id] :as job} other-resources f x y]
   (-> (d/transact node (tx-ops (f job x y) other-resources))
       (then-pull-job node id)
       (ac/exceptionally tag-update-error))))

(defn update-job
  "Submits a transaction that updates `job` to a state as result of applying
  `f` to `job` and any supplied args.

  Returns a CompletableFuture that will complete with the job after the
  transaction in case of success or will complete exceptionally with an anomaly
  in case of a transaction error or other errors.

  Functions applied after the returned future are executed on the common
  ForkJoinPool."
  {:arglists '([node job f] [node job f x])}
  ([node job f]
   (update-job+ node job nil f))
  ([node job f x]
   (update-job+ node job nil f x)))

(defn job-update-failed?
  {:arglists '([anomaly])}
  [{::anom/keys [category] ::js/keys [action]}]
  (and (= ::anom/conflict category) (= :update-job action)))

(defn fail-job [job {::anom/keys [category message]}]
  (-> (assoc job :status #fhir/code"failed")
      (dissoc :statusReason :businessStatus)
      (add-output "error-category" (name category))
      (add-output "error" (or message "empty error message"))))
