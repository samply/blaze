(ns blaze.db.kv.mem
  "In-Memory Implementation of a Key-Value Store.

  It uses sorted maps with byte array keys and values."
  (:require
    [blaze.anomaly :as ba :refer [throw-anom]]
    [blaze.byte-buffer :as bb]
    [blaze.db.kv :as kv]
    [blaze.db.kv.spec]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import
    [java.util Arrays Comparator]
    [java.lang AutoCloseable]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn- copy [^bytes bs]
  (Arrays/copyOf bs (alength bs)))


(defn- put
  "Does the same as RockDB does when filling a byte buffer."
  [buf bs]
  (let [pos (bb/position buf)
        limit (bb/limit buf)
        length (Math/min (unchecked-subtract-int limit pos) (alength ^bytes bs))]
    (bb/put-byte-array! buf bs 0 length)
    (bb/set-position! buf pos)
    (bb/set-limit! buf (unchecked-add-int pos length))
    (alength ^bytes bs)))


(defn- prev [db {x :first}]
  (let [[x & xs] (rsubseq db < (key x))]
    {:first x :rest xs}))


(def ^:private iterator-invalid-anom
  (ba/fault "The iterator is invalid."))


(def ^:private iterator-closed-anom
  (ba/fault "The iterator is closed."))


(defn- check-valid [iter]
  (when-not (kv/-valid iter)
    (throw-anom iterator-invalid-anom)))


(deftype MemKvIterator [db cursor ^:volatile-mutable closed?]
  kv/KvIterator
  (-valid [_]
    (when closed? (throw-anom iterator-closed-anom))
    (some? (@cursor :first)))

  (-seek-to-first [_]
    (when closed? (throw-anom iterator-closed-anom))
    (reset! cursor {:first (first db) :rest (rest db)}))

  (-seek-to-last [_]
    (when closed? (throw-anom iterator-closed-anom))
    (reset! cursor {:first (last db) :rest nil}))

  (-seek [_ k]
    (when closed? (throw-anom iterator-closed-anom))
    (let [[x & xs] (subseq db >= k)]
      (reset! cursor {:first x :rest xs})))

  (-seek-buffer [iter kb]
    (let [k (byte-array (bb/remaining kb))]
      (bb/copy-into-byte-array! kb k)
      (kv/-seek iter k)))

  (-seek-for-prev [_ k]
    (when closed? (throw-anom iterator-closed-anom))
    (let [[x & xs] (rsubseq db <= k)]
      (reset! cursor {:first x :rest xs})))

  (-next [iter]
    (check-valid iter)
    (swap! cursor (fn [{[x & xs] :rest}] {:first x :rest xs})))

  (-prev [iter]
    (check-valid iter)
    (swap! cursor #(prev db %)))

  (-key [_]
    (if-let [first (@cursor :first)]
      (copy (key first))
      (throw-anom iterator-invalid-anom)))

  (-key [_ buf]
    (if-let [first (@cursor :first)]
      (put buf (key first))
      (throw-anom iterator-invalid-anom)))

  (-value [_]
    (if-let [first (@cursor :first)]
      (copy (val first))
      (throw-anom iterator-invalid-anom)))

  (-value [_ buf]
    (if-let [first (@cursor :first)]
      (put buf (val first))
      (throw-anom iterator-invalid-anom)))

  AutoCloseable
  (close [_]
    (set! closed? true)))


(defn- column-family-not-found-msg [column-family]
  (format "column family `%s` not found" (name column-family)))


(deftype MemKvSnapshot [db]
  kv/KvSnapshot
  (-new-iterator [_]
    (let [db (:default db)]
      (->MemKvIterator db (atom {:rest (seq db)}) false)))

  (-new-iterator [_ column-family]
    (if-let [db (get db column-family)]
      (->MemKvIterator db (atom {:rest (seq db)}) false)
      (throw-anom (ba/not-found (column-family-not-found-msg column-family)))))

  (-snapshot-get [_ k]
    (some-> (get-in db [:default k]) (copy)))

  (-snapshot-get [_ column-family k]
    (some-> (get-in db [column-family k]) (copy)))

  AutoCloseable
  (close [_]))


(defn- assoc-copy-cf [m column-family k v]
  (when (nil? m)
    (throw-anom (ba/not-found (column-family-not-found-msg column-family))))
  (assoc m (copy k) (copy v)))


(defn- assoc-copy [m k v]
  (assoc m (copy k) (copy v)))


(defn- put-entries [db entries]
  (reduce
    (fn [db [column-family k v]]
      (if (keyword? column-family)
        (update db column-family assoc-copy-cf column-family k v)
        (update db :default assoc-copy column-family k)))
    db
    entries))


(defn- write-entries [db entries]
  (reduce
    (fn [db [op column-family k v]]
      (if (keyword? column-family)
        (case op
          :put (update db column-family assoc-copy-cf column-family k v)
          :delete (update db column-family dissoc k)
          (throw-anom (ba/unsupported (str (name op) " is not supported"))))
        (case op
          :put (update db :default assoc-copy column-family k)
          :delete (update db :default dissoc column-family)
          (throw-anom (ba/unsupported (str (name op) " is not supported"))))))
    db
    entries))


(deftype MemKvStore [db]
  kv/KvStore
  (-new-snapshot [_]
    (->MemKvSnapshot @db))

  (-get [_ k]
    (kv/snapshot-get (->MemKvSnapshot @db) k))

  (-get [_ column-family k]
    (kv/snapshot-get (->MemKvSnapshot @db) column-family k))

  (-multi-get [_ ks]
    (with-open [snapshot ^AutoCloseable (->MemKvSnapshot @db)]
      (reduce
        (fn [r k]
          (if-let [v (kv/snapshot-get snapshot k)]
            (assoc r k v)
            r))
        {}
        ks)))

  (-put [_ entries]
    (log/trace "put" (count entries) "entries")
    (swap! db put-entries entries)
    nil)

  (-put [_ k v]
    (swap! db update :default assoc-copy k v)
    nil)

  (-delete [_ ks]
    (swap! db update :default #(apply dissoc % ks))
    nil)

  (-write [_ entries]
    (swap! db write-entries entries)
    nil))


(def ^:private bytes-cmp
  (reify Comparator
    (compare [_ a b]
      (Arrays/compareUnsigned ^bytes a ^bytes b))))


(def ^:private reverse-bytes-cmp
  (reify Comparator
    (compare [_ a b]
      (Arrays/compareUnsigned ^bytes b ^bytes a))))


(defn- init-column-family [[name {:keys [reverse-comparator?]}]]
  [name (sorted-map-by (if reverse-comparator? reverse-bytes-cmp bytes-cmp))])


(defn- init-db [column-families]
  (into {} (map init-column-family) column-families))


(defmethod ig/pre-init-spec ::kv/mem [_]
  (s/keys :req-un [::kv/column-families]))


(defmethod ig/init-key ::kv/mem
  [_ {:keys [column-families init-data]}]
  (log/info "Open volatile, in-memory key-value store")
  (let [store (->MemKvStore (atom (init-db (assoc column-families :default nil))))]
    (some->> init-data (kv/put! store))
    store))
