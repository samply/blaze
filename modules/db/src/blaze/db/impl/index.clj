(ns blaze.db.impl.index
  (:require
    [blaze.coll.core :as coll]
    [blaze.db.impl.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-as-of :as resource-as-of]
    [blaze.db.impl.index.resource-handle :as rh]
    [blaze.db.impl.iterators :as i]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.kv :as kv])
  (:import
    [clojure.lang IReduceInit]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn- t-by-instant*
  [iter instant]
  (kv/seek! iter (codec/tx-by-instant-key instant))
  (when (kv/valid? iter)
    (codec/decode-t (kv/value iter))))


(defn t-by-instant
  [snapshot instant]
  (with-open [iter (kv/new-iterator snapshot :t-by-instant-index)]
    (t-by-instant* iter instant)))



;; ---- Type-Level Functions ------------------------------------------------

(defn- other-clauses-filter [snapshot tid clauses]
  (if (seq clauses)
    (filter
      (fn [resource-handle]
        (let [id (codec/id-bytes (:id resource-handle))
              hash (rh/hash resource-handle)]
          (loop [[[search-param modifier _ values] & clauses] clauses]
            (if search-param
              (when (search-param/matches? search-param snapshot tid id hash
                                           modifier values)
                (recur clauses))
              resource-handle)))))
    identity))


(defn type-query [snapshot svri rsvi raoi tid clauses start-id t]
  (let [[[search-param modifier _ values] & other-clauses] clauses]
    (coll/eduction
      (other-clauses-filter snapshot tid other-clauses)
      (search-param/resource-handles search-param snapshot svri rsvi raoi tid
                                     modifier values start-id t))))



;; ---- System-Level Functions ------------------------------------------------

(defn system-query [_ _ _ _ _ _]
  ;; TODO: implement
  [])



;; ---- Compartment-Level Functions -------------------------------------------

(defn- compartment-list-start-key [{:keys [c-hash res-id]} tid start-id]
  (if start-id
    (codec/compartment-resource-type-key c-hash res-id tid start-id)
    (codec/compartment-resource-type-key c-hash res-id tid)))


(defn- compartment-list-cmp-key [{:keys [c-hash res-id]} tid]
  (codec/compartment-resource-type-key c-hash res-id tid))


(defn compartment-list
  "Returns a reducible collection of all resource handles of type with `tid`
  linked to `compartment` and ordered by resource id.

  The list starts at `start-id`.

  The implementation uses the :resource-type-index to obtain an iterator over
  all resource handles of the type with `tid` ever known (independent from `t`).
  It then looks up the newest version of each resource in the
  :resource-as-of-index not newer then `t`."
  ^IReduceInit
  [cri raoi compartment tid start-id t]
  (let [start-key (compartment-list-start-key compartment tid start-id)
        cmp-key (compartment-list-cmp-key compartment tid)]
    (coll/eduction
      (comp
        (take-while (fn [[prefix]] (bytes/= prefix cmp-key)))
        (map (fn [[_ id]] (resource-as-of/resource-handle raoi tid id t)))
        (remove nil?)
        (remove rh/deleted?))
      (i/keys cri codec/decode-compartment-resource-type-key start-key))))


(defn compartment-query
  "Iterates over the CSV index "
  [snapshot csvri raoi compartment tid clauses t]
  (let [[[search-param _ _ values] & other-clauses] clauses]
    (coll/eduction
      (other-clauses-filter snapshot tid other-clauses)
      (search-param/compartment-resources search-param csvri raoi compartment
                                          tid values t))))
