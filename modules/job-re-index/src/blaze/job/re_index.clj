(ns blaze.job.re-index
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.job-scheduler.protocols :as p]
   [blaze.job.re-index.spec]
   [blaze.job.util :as job-util]
   [blaze.module :as m]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [integrant.core :as ig]
   [java-time.api :as time]
   [taoensso.timbre :as log])
  (:import
   [java.time ZoneOffset]))

(set! *warn-on-reflection* true)

(def ^:private parameter-system
  "https://samply.github.io/blaze/fhir/CodeSystem/ReIndexJobParameter")

(def ^:private output-system
  "https://samply.github.io/blaze/fhir/CodeSystem/ReIndexJobOutput")

(def ^:private task-output
  (partial job-util/task-output output-system))

(def ^:private initial-duration
  #fhir/Quantity
   {:value #fhir/decimal 0M
    :unit #fhir/string "s"
    :system #fhir/uri-interned "http://unitsofmeasure.org"
    :code #fhir/code "s"})

(defn- start-job [job total-resources]
  (assoc
   job
   :status #fhir/code "in-progress"
   :statusReason job-util/started-status-reason
   :output
   [(task-output "total-resources" (type/unsignedInt total-resources))
    (task-output "resources-processed" #fhir/unsignedInt 0)
    (task-output "processing-duration" initial-duration)]))

(defn- add-output [job code value]
  (job-util/add-output job output-system code value))

(defn- increment-unsigned-int [value x]
  (type/unsignedInt (+ (:value value) x)))

(defn- increment-resources-processed [job num-resources]
  (job-util/update-output-value job output-system "resources-processed"
                                increment-unsigned-int num-resources))

(defn- increment-quantity-value [quantity x]
  (update quantity :value #(type/decimal (+ (:value %) x))))

(defn- increment-duration [job duration]
  (job-util/update-output-value job output-system "processing-duration"
                                increment-quantity-value duration))

(defn- set-next [job {:fhir/keys [type] :keys [id]}]
  (if type
    (add-output job "next-resource" (type/string (str (name type) "/" id)))
    (job-util/remove-output job output-system "next-resource")))

(defn- increment-job [job {:keys [num-resources duration next]}]
  (-> (assoc job :statusReason job-util/incremented-status-reason)
      (increment-resources-processed num-resources)
      (increment-duration duration)
      (set-next next)))

(defn- complete-job [job result]
  (-> (increment-job job result)
      (assoc :status #fhir/code "completed")
      (dissoc :statusReason)))

(defn- search-param-url [job]
  (-> (job-util/input-value job parameter-system "search-param-url") :value))

(defn- next-resource [job]
  (-> (job-util/output-value job output-system "next-resource") :value))

(defn- instant [clock]
  (.atOffset (time/instant clock) ZoneOffset/UTC))

(defn- elapsed [clock job]
  (-> (time/duration (-> job :meta :lastUpdated :value) (instant clock))
      (time/as :seconds)))

(defn- update-job [{:keys [admin-node clock]} job {:keys [next] :as result}]
  (if next
    (if (< 10 (elapsed clock job))
      (job-util/update-job admin-node job increment-job result)
      (ac/completed-future (increment-job job result)))
    (job-util/update-job admin-node job complete-job result)))

(defn- assoc-duration [start result]
  (assoc result :duration (BigDecimal/valueOf (- (System/nanoTime) start) 9)))

(defn- time-future [future]
  (ac/then-apply future (partial assoc-duration (System/nanoTime))))

(defn- re-index-fn [main-db search-param-url]
  (fn
    ([]
     (-> (d/re-index main-db search-param-url)
         (time-future)))
    ([start-type start-id]
     (-> (d/re-index main-db search-param-url start-type start-id)
         (time-future)))))

(defn- continuation
  "Returns a function that takes a re-index result (or nil) and an anomaly
  (or nil), updates the job and continues processing if there is more work to
  do."
  [{:keys [admin-node] :as context} re-index job]
  (fn [{:keys [next] :as result} anomaly]
    (if anomaly
      (job-util/update-job admin-node job job-util/fail-job anomaly)
      (cond-> (update-job context job result)
        next
        (ac/then-compose
         (fn [job]
           (-> (re-index (name (:fhir/type next)) (:id next))
               (ac/handle (continuation context re-index job))
               (ac/then-compose identity))))))))

(def ^:private missing-search-param-anom
  (ba/incorrect "Missing search parameter URL."))

(defn- on-start
  [{:keys [main-node admin-node] :as context} job]
  (let [main-db (d/db main-node)]
    (if-let [search-param-url (search-param-url job)]
      (if-ok [total (d/re-index-total main-db search-param-url)]
        (let [re-index (re-index-fn main-db search-param-url)]
          (-> (job-util/update-job admin-node job start-job total)
              (ac/then-compose
               (fn [job]
                 (-> (re-index)
                     (ac/handle (continuation context re-index job))
                     (ac/then-compose identity))))))
        (partial job-util/update-job admin-node job job-util/fail-job))
      (job-util/update-job admin-node job job-util/fail-job
                           missing-search-param-anom))))

(defn- on-resume
  [{:keys [main-node] :as context} job]
  (let [main-db (d/db main-node)
        re-index (re-index-fn main-db (search-param-url job))
        [type id] (some-> (next-resource job) (str/split #"/" 2))]
    (-> (if type (re-index type id) (re-index))
        (ac/handle (continuation context re-index job))
        (ac/then-compose identity))))

(defmethod m/pre-init-spec :blaze.job/re-index [_]
  (s/keys :req-un [::main-node ::admin-node :blaze/clock]))

(defmethod ig/init-key :blaze.job/re-index
  [_ config]
  (log/info "Init re-index job handler")
  (reify p/JobHandler
    (-on-start [_ job]
      (on-start config job))
    (-on-resume [_ job]
      (on-resume config job))))

(derive :blaze.job/re-index :blaze.job/handler)
