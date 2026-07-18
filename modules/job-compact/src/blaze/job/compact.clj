(ns blaze.job.compact
  "The compaction job calls blaze.db.kv/compact! for one column family."
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [when-ok if-ok]]
   [blaze.async.comp :as ac]
   [blaze.db.kv :as kv]
   [blaze.fhir.canonical :as canonical]
   [blaze.fhir.spec.type :as type]
   [blaze.job-scheduler.protocols :as p]
   [blaze.job.compact.spec]
   [blaze.job.util :as job-util]
   [blaze.module :as m]
   [blaze.util :refer [str]]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(def ^:private parameter-system (canonical/url "CodeSystem/CompactJobParameter"))

(def ^:private output-system (canonical/url "CodeSystem/CompactJobOutput"))

(def ^:private task-output
  (partial job-util/task-output output-system))

(def ^:private initial-duration
  #fhir/Quantity
   {:value #fhir/decimal 0M
    :unit #fhir/string "s"
    :system #fhir/uri-interned "http://unitsofmeasure.org"
    :code #fhir/code "s"})

(defn job
  "Creates a compact job resource."
  [authored-on {:keys [database column-family]}]
  {:fhir/type :fhir/Task
   :meta (type/meta {:profile (mapv type/canonical (canonical/urls "StructureDefinition/CompactJob"))})
   :status #fhir/code "ready"
   :intent #fhir/code "order"
   :code (job-util/type-codeable-concept "compact" "Compact a Database Column Family")
   :authoredOn (type/dateTime authored-on)
   :input
   [{:fhir/type :fhir.Task/input
     :type (type/codeable-concept
            {:coding (canonical/codings "CodeSystem/CompactJobParameter" "database")})
     :value (type/code database)}
    {:fhir/type :fhir.Task/input
     :type (type/codeable-concept
            {:coding (canonical/codings "CodeSystem/CompactJobParameter" "column-family")})
     :value (type/code column-family)}]})

(defn- start-job [job]
  (assoc
   job
   :status #fhir/code "in-progress"
   :statusReason job-util/started-status-reason
   :output
   [(task-output "processing-duration" initial-duration)]))

(defn- increment-quantity-value [quantity x]
  (update quantity :value #(type/decimal (+ (:value %) x))))

(defn- increment-duration [job duration]
  (job-util/update-output-value job output-system "processing-duration"
                                increment-quantity-value duration))

(defn- complete-job [job {:keys [duration]}]
  (-> (increment-duration job duration)
      (assoc :status #fhir/code "completed")
      (dissoc :statusReason)))

(defn- database* [job]
  (or (:value (job-util/input-value job parameter-system "database"))
      (ba/incorrect "Missing `database` parameter.")))

(defn- database [context job]
  (when-ok [database (database* job)]
    (get context (keyword (str database "-db"))
         (ba/incorrect (format "Unknown database `%s`." database)))))

(defn- column-family [job]
  (or (some-> (job-util/input-value job parameter-system "column-family")
              :value keyword)
      (ba/incorrect "Missing `column-family` parameter.")))

(defn- assoc-duration [start result]
  (assoc result :duration (BigDecimal/valueOf (- (System/nanoTime) start) 9)))

(defn- time-future [future]
  (ac/then-apply future (partial assoc-duration (System/nanoTime))))

(defn- on-start [{:keys [admin-node] :as context} job]
  (if-ok [database (database context job)
          column-family (column-family job)]
    (-> (job-util/update-job admin-node job start-job)
        (ac/then-compose
         (fn [job]
           (-> (kv/compact! database column-family)
               (time-future)
               (ac/then-compose
                (partial job-util/update-job admin-node job complete-job))))))
    (partial job-util/update-job admin-node job job-util/fail-job)))

(defmethod m/pre-init-spec :blaze.job/compact [_]
  (s/keys :req-un [::index-db ::admin-node :blaze/clock]
          :opt-un [::transaction-db ::resource-db]))

(defmethod ig/init-key :blaze.job/compact
  [_ config]
  (log/info "Init compact job handler")
  (reify p/JobHandler
    (-on-start [_ job]
      (on-start config job))))

(derive :blaze.job/compact :blaze.job/handler)
