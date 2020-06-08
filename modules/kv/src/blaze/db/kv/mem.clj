(ns blaze.db.kv.mem
  (:require
    [blaze.db.kv :as kv]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import
    [java.io Closeable]
    [java.util Arrays Comparator]
    [java.nio ByteBuffer]))


(set! *warn-on-reflection* true)


(defn- copy [^bytes bs]
  (Arrays/copyOf bs (alength bs)))


(defn- put
  "Does the same as RockDB does when filling a byte buffer."
  [^ByteBuffer buf ^bytes bs]
  (let [pos (.position buf)
        limit (.limit buf)
        length (Math/min (unchecked-subtract-int limit pos) (alength bs))]
    (.put buf bs 0 length)
    (.position buf pos)
    (.limit buf (+ pos length))
    (alength bs)))


(deftype MemKvIterator [db cursor ^:volatile-mutable closed?]
  kv/KvIterator
  (-valid [_]
    (when closed? (throw (Exception. "closed")))
    (some? (:first @cursor)))

  (-seek-to-first [_]
    (when closed? (throw (Exception. "closed")))
    (reset! cursor {:first (first db) :rest (rest db)}))

  (-seek-to-last [_]
    (when closed? (throw (Exception. "closed")))
    (reset! cursor {:first (last db) :rest nil}))

  (-seek [_ k]
    (when closed? (throw (Exception. "closed")))
    (let [[x & xs] (subseq db >= k)]
      (reset! cursor {:first x :rest xs})))

  (-seek-for-prev [_ k]
    (when closed? (throw (Exception. "closed")))
    (let [[x & xs] (rsubseq db <= k)]
      (reset! cursor {:first x :rest xs})))

  (-next [_]
    (when closed? (throw (Exception. "closed")))
    (swap! cursor (fn [{[x & xs] :rest}] {:first x :rest xs})))

  (-prev [this]
    (when closed? (throw (Exception. "closed")))
    (when-let [prev (first (rsubseq db < (key (:first @cursor))))]
      (kv/seek! this (key prev))))

  (-key [_]
    (when closed? (throw (Exception. "closed")))
    (-> @cursor :first key copy))

  (-key [_ buf]
    (when closed? (throw (Exception. "closed")))
    (put buf (-> @cursor :first key)))

  (-value [_]
    (when closed? (throw (Exception. "closed")))
    (-> @cursor :first val copy))

  (-value [_ buf]
    (when closed? (throw (Exception. "closed")))
    (put buf (-> @cursor :first val)))

  Closeable
  (close [_]
    (set! closed? true)))


(defn- column-family-unknown [column-family]
  (RuntimeException. (format "column family %s unknown" column-family)))


(deftype MemKvSnapshot [db]
  kv/KvSnapshot
  (new-iterator [_]
    (let [db (:default db)]
      (->MemKvIterator db (atom {:rest (seq db)}) false)))

  (new-iterator [_ column-family]
    (if-let [db (get db column-family)]
      (->MemKvIterator db (atom {:rest (seq db)}) false)
      (throw (column-family-unknown column-family))))

  (snapshot-get [_ k]
    (some-> (get-in db [:default k]) (copy)))

  (snapshot-get [_ column-family k]
    (some-> (get-in db [column-family k]) (copy)))

  Closeable
  (close [_]))


(defn- assoc-copy [m ^bytes k ^bytes v]
  (assoc m (copy k) (copy v)))


(defn- put-entries [db entries]
  (reduce
    (fn [db [column-family k v]]
      (if (keyword? column-family)
        (update db column-family assoc-copy k v)
        (update db :default assoc-copy column-family k)))
    db
    entries))


(defn- write-entries [db entries]
  (reduce
    (fn [db [op column-family k v]]
      (if (keyword? column-family)
        (case op
          :put (update db column-family assoc-copy k v)
          :merge (throw (UnsupportedOperationException. "merge is not supported"))
          :delete (update db column-family dissoc k))
        (case op
          :put (update db :default assoc-copy column-family k)
          :merge (throw (UnsupportedOperationException. "merge is not supported"))
          :delete (update db :default dissoc column-family))))
    db
    entries))


(deftype MemKvStore [db]
  kv/KvStore
  (new-snapshot [_]
    (->MemKvSnapshot @db))

  (get [_ k]
    (kv/snapshot-get (->MemKvSnapshot @db) k))

  (get [_ column-family k]
    (kv/snapshot-get (->MemKvSnapshot @db) column-family k))

  (-put [_ entries]
    (swap! db put-entries entries)
    nil)

  (-put [_ k v]
    (swap! db update :default assoc-copy k v)
    nil)

  (delete [_ ks]
    (swap! db #(apply dissoc % ks))
    nil)

  (write [_ entries]
    (swap! db write-entries entries)
    nil))


(def ^:private bytes-cmp
  (reify Comparator
    (compare [_ a b]
      (Arrays/compareUnsigned ^bytes a ^bytes b))))


(defn- init-db [column-families]
  (into {} (map (fn [cf] [cf (sorted-map-by bytes-cmp)])) column-families))


(defn init-mem-kv-store
  "Initializes an in-memory key-value store with optional column families."
  ([]
   (init-mem-kv-store {}))
  ([column-families]
   (->MemKvStore (atom (init-db (conj (keys column-families) :default))))))


(defmethod ig/init-key :blaze.db.kv/mem
  [_ {:keys [column-families]}]
  (log/info "Open volatile, in-memory key-value store")
  (init-mem-kv-store column-families))


(derive :blaze.db.kv/mem :blaze.db/kv-store)
