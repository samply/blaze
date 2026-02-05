(ns blaze.db.impl.query.type
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.async.comp :as ac]
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index :as index]
   [blaze.db.impl.index.resource-as-of :as rao]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.index.type-stats :as type-stats]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.query.util :as qu]
   [blaze.db.impl.search-param.chained :as spc]))

(defrecord TypeQuery [tid clauses]
  p/Query
  (-count [_ batch-db]
    (index/type-query-total batch-db tid (:search-clauses clauses)))
  (-execute [_ batch-db]
    (index/type-query batch-db tid clauses))
  (-execute [_ batch-db start-id]
    (index/type-query batch-db tid clauses (codec/id-byte-string start-id)))
  (-query-clauses [_]
    (qu/decode-clauses clauses))
  (-query-plan [_ batch-db]
    (index/type-query-plan batch-db tid clauses)))

(def ^:private ^:const ^long patient-compartment-hash (codec/c-hash "Patient"))
(def ^:private ^:const ^long patient-code-hash (codec/c-hash "patient"))

(defn- resource-handle-not-found-msg [{:keys [t since-t]} tid id]
  (format "Resource handle `%s/%s` not found in database with t=%d and since-t=%d."
          (codec/tid->type tid) (codec/id-string id) t since-t))

(defn- non-deleted-resource-handle* [batch-db tid id]
  (when-let [handle (p/-resource-handle batch-db tid id)]
    (when-not (rh/deleted? handle)
      handle)))

(defn- non-deleted-resource-handle [batch-db tid id]
  (or (non-deleted-resource-handle* batch-db tid id)
      (ba/fault (resource-handle-not-found-msg batch-db tid id))))

(defn- first-referenced-patient-not-found-msg [{:keys [t since-t]} resource-handle]
  (format "Patient resource handle referenced from `%s/%s` not found in database with t=%d and since-t=%d."
          (name (:fhir/type resource-handle)) (:id resource-handle) t since-t))

(defn- first-referenced-patient [batch-db resource-handle]
  (or (coll/first (spc/targets batch-db resource-handle patient-code-hash))
      (ba/fault (first-referenced-patient-not-found-msg batch-db resource-handle))))

(defn- start-patient-id [batch-db tid start-id]
  (when-ok [start-handle (non-deleted-resource-handle batch-db tid start-id)
            start-patient-handle (first-referenced-patient batch-db start-handle)]
    (codec/id-byte-string (:id start-patient-handle))))

;; A type query over resources with `tid` and patients with `patient-ids`.
(defrecord PatientTypeQuery [tid patient-ids compartment-clause scan-clauses
                             other-clauses compartment-query]
  p/Query
  (-count [query batch-db]
    (ac/completed-future (count (p/-execute query batch-db))))
  (-execute [_ batch-db]
    (coll/eduction (mapcat #(compartment-query batch-db %)) patient-ids))
  (-execute [_ batch-db start-id]
    (let [start-id (codec/id-byte-string start-id)]
      (when-ok [start-patient-id (start-patient-id batch-db tid start-id)]
        (coll/eduction
         cat
         [(compartment-query batch-db start-patient-id start-id)
          (coll/eduction
           (comp (drop-while #(not= start-patient-id %))
                 (drop 1)
                 (mapcat #(compartment-query batch-db %)))
           patient-ids)]))))
  (-query-clauses [_]
    (qu/decode-clauses {:search-clauses (-> [[compartment-clause]]
                                            (into scan-clauses)
                                            (into other-clauses))}))
  (-query-plan [_ _]
    (index/compartment-query-plan scan-clauses other-clauses)))

(defn patient-type-query
  [tid patient-ids compartment-clause scan-clauses other-clauses]
  (->PatientTypeQuery
   tid patient-ids compartment-clause scan-clauses other-clauses
   (fn
     ([batch-db patient-id]
      (index/compartment-query batch-db [patient-compartment-hash patient-id]
                               tid scan-clauses other-clauses))
     ([batch-db patient-id start-id]
      (index/compartment-query batch-db [patient-compartment-hash patient-id]
                               tid scan-clauses other-clauses start-id)))))

(defrecord EmptyTypeQuery [tid]
  p/Query
  (-count [_ batch-db]
    (ac/completed-future
     (:total (type-stats/seek-value (:snapshot batch-db) tid (:t batch-db)) 0)))
  (-execute [_ batch-db]
    (rao/type-list batch-db tid))
  (-execute [_ batch-db start-id]
    (rao/type-list batch-db tid (codec/id-byte-string start-id)))
  (-query-clauses [_])
  (-query-plan [_ _]
    {:query-type :type}))
