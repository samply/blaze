(ns blaze.job-scheduler.job-util
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

(def ^:private output-system
  "https://samply.github.io/blaze/fhir/CodeSystem/JobOutput")

(defn- mk-status-reason [reason]
  (type/map->CodeableConcept
   {:coding
    [(type/map->Coding
      {:system (type/uri status-reason-url)
       :code (type/code reason)})]}))

(def orderly-shut-down-status-reason (mk-status-reason "orderly-shutdown"))
(def paused-status-reason (mk-status-reason "paused"))
(def resumed-status-reason (mk-status-reason "resumed"))
(def incremented-status-reason (mk-status-reason "incremented"))
(def started-status-reason (mk-status-reason "started"))

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
  {:arglists '([job])}
  [{:keys [code]}]
  (keyword (code-value type-url code)))

(defn status-reason
  {:arglists '([job])}
  [{:keys [statusReason]}]
  (code-value status-reason-url statusReason))

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

(defn error-msg [job]
  (output-value job "error"))

(defn- update-output-value* [system code f x output]
  (cond-> output
    (= code (code-value system (:type output)))
    (update :value f x)))

(defn update-output-value
  [job system code f x]
  (update job :output #(mapv (partial update-output-value* system code f x) %)))

(defn task-output [system code value]
  {:fhir/type :fhir.Task/output
   :type (type/map->CodeableConcept
          {:coding
           [(type/map->Coding
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

(defn update-job
  "Submits a transaction that updates `job` to a state as result of applying
  `f` to `job` and any supplied args.

  Returns a CompletableFuture that will complete with the job after the
  transaction in case of success or will complete exceptionally with an anomaly
  in case of a transaction error or other errors."
  {:arglists '([node job f] [node job f x])}
  ([node {:keys [id] :as job} f]
   (-> (d/transact node [(update-tx-op (f job))])
       (ac/then-compose #(d/pull node (d/resource-handle % "Task" id)))
       (ac/exceptionally #(assoc % ::js/action :update-job))))
  ([node {:keys [id] :as job} f x]
   (-> (d/transact node [(update-tx-op (f job x))])
       (ac/then-compose #(d/pull node (d/resource-handle % "Task" id)))
       (ac/exceptionally #(assoc % ::js/action :update-job)))))

(defn job-update-failed? [{::anom/keys [category] ::js/keys [action]}]
  (and (= ::anom/conflict category) (= :update-job action)))

(defn fail-job [job {::anom/keys [message]}]
  (-> (assoc job :status #fhir/code"failed")
      (dissoc :statusReason)
      (add-output "error" (or message "empty error message"))))
