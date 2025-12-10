(ns blaze.db.kv.mem
  "In-Memory Implementation of a Key-Value Store.

  It uses sorted maps with byte array keys and values."
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [throw-anom]]
   [blaze.async.comp :as ac]
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.db.kv :as kv]
   [blaze.db.kv.protocols :as p]
   [blaze.db.kv.spec]
   [blaze.module :as m]
   [blaze.util :refer [str]]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [taoensso.timbre :as log])
  (:import
   [java.lang AutoCloseable]
   [java.util Arrays Comparator]))

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
  (when-not (p/-valid iter)
    (throw-anom iterator-invalid-anom)))

(deftype MemKvIterator [db cursor ^:volatile-mutable closed?]
  p/KvIterator
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
      (p/-seek iter k)))

  (-seek-for-prev [_ k]
    (when closed? (throw-anom iterator-closed-anom))
    (let [[x & xs] (rsubseq db <= k)]
      (reset! cursor {:first x :rest xs})))

  (-seek-for-prev-buffer [iter kb]
    (let [k (byte-array (bb/remaining kb))]
      (bb/copy-into-byte-array! kb k)
      (p/-seek-for-prev iter k)))

  (-next [iter]
    (check-valid iter)
    (swap! cursor (fn [{[x & xs] :rest}] {:first x :rest xs})))

  (-prev [iter]
    (check-valid iter)
    (swap! cursor #(prev db %)))

  (-key [iter]
    (check-valid iter)
    (copy (key (@cursor :first))))

  (-key [iter buf]
    (check-valid iter)
    (put buf (key (@cursor :first))))

  (-value [iter]
    (check-valid iter)
    (copy (val (@cursor :first))))

  (-value [iter buf]
    (check-valid iter)
    (put buf (val (@cursor :first))))

  AutoCloseable
  (close [_]
    (set! closed? true)))

(defn- column-family-not-found-msg [column-family]
  (format "Column family `%s` not found." (name column-family)))

(defn- column-family-not-found-anom [column-family]
  (ba/not-found (column-family-not-found-msg column-family)))

(deftype MemKvSnapshot [db]
  p/KvSnapshot
  (-new-iterator [_ column-family]
    (if-let [db (get db column-family)]
      (->MemKvIterator db (atom {:rest (seq db)}) false)
      (throw-anom (column-family-not-found-anom column-family))))

  (-snapshot-get [_ column-family k]
    (some-> (get-in db [column-family k]) (copy)))

  AutoCloseable
  (close [_]))

(defn- assoc-copy [m column-family k v]
  (when (nil? m)
    (throw-anom (column-family-not-found-anom column-family)))
  (assoc m (copy k) (copy v)))

(defn- put-entries [db entries]
  (reduce
   (fn [db [column-family k v]]
     (update db column-family assoc-copy column-family k v))
   db
   entries))

(defn- delete-entries [db entries]
  (reduce
   (fn [db [column-family k]]
     (update db column-family dissoc k))
   db
   entries))

(defn- write-entries [db entries]
  (reduce
   (fn [db [op column-family k v]]
     (case op
       :put (update db column-family assoc-copy column-family k v)
       :delete (update db column-family dissoc k)
       (throw-anom (ba/unsupported (str (name op) " is not supported")))))
   db
   entries))

(deftype MemKvStore [db]
  p/KvStore
  (-new-snapshot [_]
    (->MemKvSnapshot @db))

  (-get [_ column-family k]
    (kv/snapshot-get (->MemKvSnapshot @db) column-family k))

  (-put [_ entries]
    (swap! db put-entries entries)
    nil)

  (-delete [_ entries]
    (swap! db delete-entries entries)
    nil)

  (-write [_ entries]
    (swap! db write-entries entries)
    nil)

  (-estimate-num-keys [_ column-family]
    (if-let [m (get @db column-family)]
      (count m)
      (column-family-not-found-anom column-family)))

  (-estimate-scan-size [_ column-family key-range]
    (if-let [m (get @db column-family)]
      (let [[start-key end-key] key-range]
        (count (subseq m >= (bs/to-byte-array start-key) < (bs/to-byte-array end-key))))
      (column-family-not-found-anom column-family)))

  (-compact [_ column-family]
    (ac/completed-future
     (when-not (get @db column-family)
       (column-family-not-found-anom column-family)))))

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

(defmethod m/pre-init-spec ::kv/mem [_]
  (s/keys :req-un [::kv/column-families]))

(defmethod ig/init-key ::kv/mem
  [_ {:keys [column-families init-data]}]
  (log/info "Open volatile, in-memory key-value store")
  (let [store (->MemKvStore (atom (init-db (assoc column-families :default nil))))]
    (some->> init-data (kv/put! store))
    store))
