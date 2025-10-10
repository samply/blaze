(ns blaze.job.async-interaction
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.handler.fhir.util.spec]
   [blaze.job-scheduler.protocols :as p]
   [blaze.job.async-interaction.spec]
   [blaze.job.async-interaction.util :as u]
   [blaze.job.util :as job-util]
   [blaze.module :as m]
   [blaze.rest-api :as-alias rest-api]
   [blaze.spec]
   [blaze.util :refer [str]]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(def output-uri
  "https://samply.github.io/blaze/fhir/CodeSystem/AsyncInteractionJobOutput")

(defn job
  "Creates a async interaction job resource."
  [authored-on bundle-id t]
  {:fhir/type :fhir/Task
   :meta #fhir/Meta{:profile [#fhir/canonical "https://samply.github.io/blaze/fhir/StructureDefinition/AsyncInteractionJob"]}
   :status #fhir/code "ready"
   :intent #fhir/code "order"
   :code #fhir/CodeableConcept
          {:coding
           [#fhir/Coding
             {:system #fhir/uri-interned "https://samply.github.io/blaze/fhir/CodeSystem/JobType"
              :code #fhir/code "async-interaction"
              :display #fhir/string-interned "Asynchronous Interaction Request"}]}
   :authoredOn (type/dateTime authored-on)
   :input
   [(u/request-bundle-input (str "Bundle/" bundle-id))
    {:fhir/type :fhir.Task/input
     :type (type/codeable-concept
            {:coding
             [(type/coding
               {:system (type/uri-interned u/parameter-uri)
                :code #fhir/code "t"})]})
     :value (type/unsignedInt t)}]})

(defn- return-preference-extension [return-preference]
  (type/extension
   {:url "https://samply.github.io/blaze/fhir/StructureDefinition/return-preference"
    :value (type/code (name return-preference))}))

(defn request-bundle
  ([id method url]
   (request-bundle id method url nil {}))
  ([id method url resource {return-preference :blaze.preference/return}]
   {:fhir/type :fhir/Bundle
    :id id
    :type #fhir/code "batch"
    :entry
    [(cond->
      {:fhir/type :fhir.Bundle/entry
       :request
       (cond->
        {:fhir/type :fhir.Bundle.entry/request
         :method (type/code method)
         :url (type/uri url)}
         return-preference
         (assoc :extension [(return-preference-extension return-preference)]))}
       resource
       (assoc :resource resource))]}))

(defn- start-job [job]
  (assoc
   job
   :status #fhir/code "in-progress"
   :statusReason job-util/started-status-reason))

(defn t [job]
  (:value (job-util/input-value job u/parameter-uri "t")))

(defn response-bundle-ref
  "Returns the reference to the response bundle of `job` or nil if there is none."
  [job]
  (-> (job-util/output-value job output-uri "bundle") :reference :value))

(defn- response-bundle [context entries]
  {:fhir/type :fhir/Bundle
   :id (m/luid context)
   :type #fhir/code "batch-response"
   :entry entries})

(defn- process-batch-entries
  [{:keys [main-node db-sync-timeout] ::keys [running-jobs] :as context}
   {job-id :id} t entries]
  (-> (fhir-util/sync main-node t db-sync-timeout)
      (ac/then-compose-async
       (fn [db]
         (let [context (assoc
                        context
                        :blaze/db db
                        :blaze/cancelled? #(when (get @running-jobs job-id)
                                             (ba/interrupted)))]
           (do-sync [entries (fhir-util/process-batch-entries context entries)]
             (response-bundle context entries)))))))

(defn add-response-bundle-reference [job response-bundle-id]
  (->> (type/reference {:reference (type/string (str "Bundle/" response-bundle-id))})
       (job-util/add-output job output-uri "bundle")))

(defn- add-processing-duration [job start]
  (job-util/add-output job output-uri "processing-duration"
                       (u/processing-duration start)))

(defn- complete-job [job response-bundle-id start]
  (-> (assoc job :status #fhir/code "completed")
      (dissoc :statusReason :businessStatus)
      (add-response-bundle-reference response-bundle-id)
      (add-processing-duration start)))

(defn- finish-cancellation [job]
  (assoc job :businessStatus job-util/cancellation-finished-sub-status))

(defn- on-start
  [{:keys [admin-node] ::keys [running-jobs] :as context} job]
  (-> (u/pull-request-bundle admin-node job)
      (ac/then-compose
       (fn [{entries :entry}]
         (if-let [t (t job)]
           (-> (job-util/update-job admin-node job start-job)
               (ac/then-compose
                (fn [{:keys [id] :as job}]
                  (swap! running-jobs assoc id false)
                  (let [start (System/nanoTime)]
                    (-> (process-batch-entries context job t entries)
                        (ac/then-compose
                         (fn [{bundle-id :id :as bundle}]
                           (job-util/update-job+ admin-node job [bundle] complete-job
                                                 bundle-id start)))
                        (ac/exceptionally-compose
                         (fn [e]
                           (if (ba/interrupted? e)
                             (-> (job-util/pull-job admin-node id)
                                 (ac/then-compose
                                  #(job-util/update-job admin-node % finish-cancellation)))
                             (ac/completed-future e))))
                        (ac/when-complete
                         (fn [_ _]
                           (swap! running-jobs dissoc id))))))))
           (ac/completed-future (ba/incorrect "Missing database point in time.")))))))

(defmethod m/pre-init-spec :blaze.job/async-interaction [_]
  (s/keys :req [:blaze/base-url]
          :req-un [::main-node ::admin-node ::rest-api/batch-handler
                   ::rest-api/db-sync-timeout :blaze/context-path]))

(defmethod ig/init-key :blaze.job/async-interaction
  [_ config]
  (log/info "Init async-interaction job handler")
  (let [context (assoc config ::running-jobs (atom {}))]
    (reify p/JobHandler
      (-on-start [_ job]
        (on-start context job))
      (-on-cancel [_ job]
        (swap! (::running-jobs context) assoc (:id job) true)
        (ac/completed-future job)))))

(derive :blaze.job/async-interaction :blaze.job/handler)
