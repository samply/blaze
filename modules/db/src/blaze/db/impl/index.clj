(ns blaze.db.impl.index
  (:require
    [blaze.coll.core :as coll]
    [blaze.db.bytes :as bytes]
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

(defn- other-clauses-filter [context tid clauses]
  (if (seq clauses)
    (filter
      (fn [resource-handle]
        (let [id (codec/id-bytes (:id resource-handle))
              hash (rh/hash resource-handle)]
          (loop [[[search-param modifier _ values] & clauses] clauses]
            (if search-param
              (when (search-param/matches? search-param context tid id
                                           hash modifier values)
                (recur clauses))
              resource-handle)))))
    identity))


(defn type-query [context tid clauses start-id]
  (let [[[search-param modifier _ values] & other-clauses] clauses]
    (coll/eduction
      (other-clauses-filter context tid other-clauses)
      (search-param/resource-handles search-param context tid
                                     modifier values start-id))))



;; ---- System-Level Functions ------------------------------------------------

(defn system-query [_ _]
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
  [{:keys [cri] :as context} compartment tid start-id]
  (let [start-key (compartment-list-start-key compartment tid start-id)
        cmp-key (compartment-list-cmp-key compartment tid)]
    (coll/eduction
      (comp
        (take-while (fn [[prefix]] (bytes/= prefix cmp-key)))
        (map (fn [[_ id]] (resource-as-of/resource-handle context tid id)))
        (remove nil?)
        (remove rh/deleted?))
      (i/keys cri codec/decode-compartment-resource-type-key start-key))))


(defn compartment-query
  "Iterates over the CSV index "
  [context compartment tid clauses]
  (let [[[search-param _ _ values] & other-clauses] clauses]
    (coll/eduction
      (other-clauses-filter context tid other-clauses)
      (search-param/compartment-resource-handles
        search-param context compartment tid values))))
