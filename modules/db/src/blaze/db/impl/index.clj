(ns blaze.db.impl.index
  (:require
    [blaze.coll.core :as coll]
    [blaze.db.impl.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource :as resource]
    [blaze.db.impl.index.resource-as-of :as resource-as-of]
    [blaze.db.impl.iterators :as i]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.impl.util :as util]
    [blaze.db.kv :as kv]
    [taoensso.nippy :as nippy])
  (:import
    [blaze.db.impl.index.resource Hash]
    [clojure.lang IReduceInit]
    [java.nio ByteBuffer]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn tx [kv-store t]
  (resource/tx kv-store t))


(defn load-resource-content [kv-store ^Hash hash]
  (some-> (kv/get kv-store :resource-index (.hash hash)) (nippy/fast-thaw)))


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

(def ^:private by-id-grouper
  "Transducer which groups `[id hash-prefix]` tuples by `id` and concatenates
  all hash-prefixes within each group, outputting `[id hash-prefixes]` tuples."
  (comp
    (partition-by (fn [[_ id]] (ByteBuffer/wrap id)))
    (map
      (fn group-hash-prefixes [[[_ id hash-prefix] & more]]
        [id (cons hash-prefix (map #(nth % 2) more))]))))


(defn- non-deleted-resource [node raoi tid id t]
  (when-let [resource (resource-as-of/resource node raoi tid id t)]
    (when-not (resource/deleted? resource)
      resource)))


(defn- resource-mapper [node raoi tid t]
  (mapcat
    (fn [[id hash-prefixes]]
      (when-let [resource (non-deleted-resource node raoi tid id t)]
        [[resource hash-prefixes]]))))


(def ^:private matches-hash-prefixes-filter
  (mapcat
    (fn [[resource hash-prefixes]]
      (when (some #(bytes/starts-with? (resource/hash resource) %) hash-prefixes)
        [resource]))))


(defn- other-clauses-filter [snapshot tid clauses]
  (if (seq clauses)
    (filter
      (fn [resource]
        (let [id (codec/id-bytes (:id resource))
              hash (resource/hash resource)]
          (loop [[[search-param values] & clauses] clauses]
            (if search-param
              (when (search-param/matches? search-param snapshot tid id hash values)
                (recur clauses))
              resource)))))
    identity))


(defn type-query [node snapshot svri raoi tid clauses t]
  (let [[[search-param values] & other-clauses] clauses]
    (coll/eduction
      (util/comp
        by-id-grouper
        (resource-mapper node raoi tid t)
        matches-hash-prefixes-filter
        (other-clauses-filter snapshot tid other-clauses))
      (search-param/keys search-param snapshot svri tid values))))



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
  "Returns a reducible collection of all resources of type with `tid` linked to
  `compartment` and ordered by resource id.

  The list starts at `start-id`.

  The implementation uses the :resource-type-index to obtain an iterator over
  all resources of the type with `tid` ever known (independent from `t`). It
  then looks up the newest version of each resource in the :resource-as-of-index
  not newer then `t`."
  ^IReduceInit
  [node cri raoi compartment tid start-id t]
  (let [start-key (compartment-list-start-key compartment tid start-id)
        cmp-key (compartment-list-cmp-key compartment tid)]
    (coll/eduction
      (comp
        (take-while (fn [[prefix]] (bytes/= prefix cmp-key)))
        (map (fn [[_ id]] (resource-as-of/resource node raoi tid id t)))
        (remove nil?)
        (remove resource/deleted?))
      (i/keys cri codec/decode-compartment-resource-type-key start-key))))


(defn compartment-query
  "Iterates over the CSV index "
  [node snapshot csvri raoi compartment tid clauses t]
  (let [[[search-param values] & other-clauses] clauses]
    (coll/eduction
      (util/comp
        by-id-grouper
        (resource-mapper node raoi tid t)
        matches-hash-prefixes-filter
        (other-clauses-filter snapshot tid other-clauses))
      (search-param/compartment-keys search-param csvri compartment tid values))))
