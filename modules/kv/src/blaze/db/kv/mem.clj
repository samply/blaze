(ns blaze.db.kv.mem
  "In-Memory Implementation of a Key-Value Store.

  It uses sorted maps with byte array keys and values."
  (:require
    [blaze.anomaly :refer [throw-anom]]
    [blaze.db.kv :as kv]
    [blaze.db.kv.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
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


(defn- prev [db {x :first}]
  (let [[x & xs] (rsubseq db < (key x))]
    {:first x :rest xs}))


(defn- check-valid [iter]
  (when (not (kv/valid? iter))
    (throw-anom ::anom/fault "The iterator is invalid.")))


(deftype MemKvIterator [db cursor ^:volatile-mutable closed?]
  kv/KvIterator
  (-valid [_]
    (when closed? (throw-anom ::anom/fault "The iterator is closed."))
    (some? (:first @cursor)))

  (-seek-to-first [_]
    (when closed? (throw-anom ::anom/fault "The iterator is closed."))
    (reset! cursor {:first (first db) :rest (rest db)}))

  (-seek-to-last [_]
    (when closed? (throw-anom ::anom/fault "The iterator is closed."))
    (reset! cursor {:first (last db) :rest nil}))

  (-seek [_ k]
    (when closed? (throw-anom ::anom/fault "The iterator is closed."))
    (let [[x & xs] (subseq db >= k)]
      (reset! cursor {:first x :rest xs})))

  (-seek-buffer [iter kb]
    (let [k (byte-array (.remaining ^ByteBuffer kb))]
      (.get ^ByteBuffer kb k)
      (kv/-seek iter k)))

  (-seek-for-prev [_ k]
    (when closed? (throw-anom ::anom/fault "The iterator is closed."))
    (let [[x & xs] (rsubseq db <= k)]
      (reset! cursor {:first x :rest xs})))

  (-next [iter]
    (check-valid iter)
    (swap! cursor (fn [{[x & xs] :rest}] {:first x :rest xs})))

  (-prev [iter]
    (check-valid iter)
    (swap! cursor #(prev db %)))

  (-key [iter]
    (check-valid iter)
    (-> @cursor :first key copy))

  (-key [iter buf]
    (check-valid iter)
    (put buf (-> @cursor :first key)))

  (-value [iter]
    (check-valid iter)
    (-> @cursor :first val copy))

  (-value [iter buf]
    (check-valid iter)
    (put buf (-> @cursor :first val)))

  Closeable
  (close [_]
    (set! closed? true)))


(defn- column-family-unknown-msg [column-family]
  (format "column family %s is unknown" column-family))


(deftype MemKvSnapshot [db]
  kv/KvSnapshot
  (-new-iterator [_]
    (let [db (:default db)]
      (->MemKvIterator db (atom {:rest (seq db)}) false)))

  (-new-iterator [_ column-family]
    (if-let [db (get db column-family)]
      (->MemKvIterator db (atom {:rest (seq db)}) false)
      (throw-anom ::anom/fault (column-family-unknown-msg column-family))))

  (-snapshot-get [_ k]
    (some-> (get-in db [:default k]) (copy)))

  (-snapshot-get [_ column-family k]
    (some-> (get-in db [column-family k]) (copy)))

  Closeable
  (close [_]))


(defn- assoc-copy [m ^bytes k ^bytes v]
  (assert m "column-family not found")
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
          :delete (update db column-family dissoc k)
          (throw-anom ::anom/unsupported (str (name op) " is not supported")))
        (case op
          :put (update db :default assoc-copy column-family k)
          :delete (update db :default dissoc column-family)
          (throw-anom ::anom/unsupported (str (name op) " is not supported")))))
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
    (with-open [snapshot ^Closeable (->MemKvSnapshot @db)]
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
  [_ {:keys [column-families]}]
  (log/info "Open volatile, in-memory key-value store")
  (->MemKvStore (atom (init-db (assoc column-families :default nil)))))
