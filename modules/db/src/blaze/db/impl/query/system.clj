(ns blaze.db.impl.query.system
  (:require
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index :as index]
   [blaze.db.impl.index.resource-as-of :as rao]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.query.util :as qu]))

(set! *warn-on-reflection* true)

(defn- type-query-total [batch-db clauses tid]
  (index/type-query-total batch-db tid (:search-clauses clauses)))

(defn- type-queries [batch-db clauses]
  (mapcat #(index/type-query batch-db % clauses)))

(defn- tid-lt
  "Returns true if `tid` comes before `start-tid` in the order of the
  ResourceAsOf index, where tids are compared as unsigned integers."
  [start-tid tid]
  (neg? (Integer/compareUnsigned (int tid) (int start-tid))))

;; A query over resources of all types with `tids`, ordered by the type hashes
;; in `tids` and resource id, matching the ResourceAsOf index order.
(defrecord SystemQuery [tids clauses]
  p/Query
  (-count [_ batch-db]
    (let [futures (mapv (partial type-query-total batch-db clauses) tids)]
      (do-sync [_ (ac/all-of futures)]
        (transduce (map ac/join) + futures))))
  (-execute [_ batch-db]
    (coll/eduction (type-queries batch-db clauses) tids))
  (-execute [_ batch-db start-type start-id]
    (let [start-tid (codec/tid start-type)
          [tid & tids] (drop-while (partial tid-lt start-tid) tids)]
      (cond
        (nil? tid) []

        (= start-tid tid)
        (coll/eduction
         cat
         [(index/type-query batch-db tid clauses (codec/id-byte-string start-id))
          (coll/eduction (type-queries batch-db clauses) tids)])

        :else
        (coll/eduction (type-queries batch-db clauses) (cons tid tids)))))
  (-query-clauses [_]
    (qu/decode-clauses clauses))
  (-query-plan [_ _]
    {:query-type :system}))

(defrecord EmptySystemQuery []
  p/Query
  (-count [_ batch-db]
    (ac/completed-future (p/-system-total batch-db)))
  (-execute [_ batch-db]
    (rao/system-list batch-db))
  (-execute [_ batch-db start-type start-id]
    (rao/system-list batch-db (codec/tid start-type)
                     (codec/id-byte-string start-id)))
  (-query-clauses [_])
  (-query-plan [_ _]
    {:query-type :system}))

(defn- unsigned-tid-comparator [tid-1 tid-2]
  (Integer/compareUnsigned (int tid-1) (int tid-2)))

(def ^:private all-tids
  "The type identifiers of all FHIR resource types in the order of the
  ResourceAsOf index."
  (vec (sort unsigned-tid-comparator (map codec/tid codec/all-types))))

(defn system-query
  "Creates a system query over resources of all types matching `clauses`."
  [clauses]
  (->SystemQuery all-tids clauses))
