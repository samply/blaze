(ns blaze.db.kv.mem
  (:require
    [blaze.db.kv :as kv]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import
    [java.io Closeable]
    [java.util Arrays Comparator]))


(set! *warn-on-reflection* true)


(deftype MemKvIterator [db cursor]
  kv/KvIterator
  (-seek [_ k]
    (let [[x & xs] (subseq db >= k)]
      (some-> (reset! cursor {:first x :rest xs})
              :first
              (key))))

  (-seek-for-prev [_ k]
    (let [[x & xs] (rsubseq db <= k)]
      (some-> (reset! cursor {:first x :rest xs})
              :first
              (key))))

  (seek-to-first [_]
    (some-> (reset! cursor {:first (first db) :rest (rest db)})
            :first
            (key)))

  (seek-to-last [_]
    (some-> (reset! cursor {:first (last db) :rest nil})
            :first
            (key)))

  (next [_]
    (some-> (swap! cursor (fn [{[x & xs] :rest}]
                            {:first x :rest xs}))
            :first
            (key)))

  (prev [this]
    (when-let [prev (first (rsubseq db < (key (:first @cursor))))]
      (kv/seek this (key prev))))


  (valid [_]
    (some? (:first @cursor)))

  (key [_]
    (some-> @cursor :first key))

  (value [_]
    (some-> @cursor :first val))

  Closeable
  (close [_]))


(defn- column-family-unknown [column-family]
  (RuntimeException. (format "column family %s unknown" column-family)))


(deftype MemKvSnapshot [db]
  kv/KvSnapshot
  (new-iterator [_]
    (let [db (:default db)]
      (->MemKvIterator db (atom {:rest (seq db)}))))

  (new-iterator [_ column-family]
    (if-let [db (get db column-family)]
      (->MemKvIterator db (atom {:rest (seq db)}))
      (throw (column-family-unknown column-family))))

  (snapshot-get [_ k]
    (get-in db [:default k]))

  (snapshot-get [_ column-family k]
    (get-in db [column-family k]))

  Closeable
  (close [_]))


(defn- put-entries [db entries]
  (reduce
    (fn [db [column-family k v]]
      (if (keyword? column-family)
        (assoc-in db [column-family k] v)
        (assoc-in db [:default column-family] k)))
    db
    entries))


(defn- write-entries [db merge-ops entries]
  (reduce
    (fn [db [op column-family k v]]
      (if (keyword? column-family)
        (case op
          :put (assoc-in db [column-family k] v)
          :merge (update-in db [column-family k] (get merge-ops column-family) v)
          :delete (update db column-family dissoc k))
        (case op
          :put (assoc-in db [:default column-family] k)
          :merge (throw (UnsupportedOperationException. "merge is not supported on default column family"))
          :delete (update db :default dissoc column-family))))
    db
    entries))


(deftype MemKvStore [db merge-ops]
  kv/KvStore
  (new-snapshot [_]
    (->MemKvSnapshot @db))

  (get [_ k]
    (get-in @db [:default k]))

  (get [_ column-family k]
    (get-in @db [column-family k]))

  (-put [_ entries]
    (swap! db put-entries entries)
    nil)

  (-put [_ k v]
    (swap! db assoc-in [:default k] v)
    nil)

  (delete [_ ks]
    (swap! db #(apply dissoc % ks))
    nil)

  (write [_ entries]
    (swap! db write-entries merge-ops entries)
    nil))


(def ^:private bytes-cmp
  (reify Comparator
    (compare [_ a b]
      (Arrays/compareUnsigned ^bytes a ^bytes b))))


(defn- init-db [column-families]
  (into {} (map (fn [cf] [cf (sorted-map-by bytes-cmp)])) column-families))


(defn init-mem-kv-store
  ([]
   (init-mem-kv-store {}))
  ([column-families]
   (->MemKvStore
     (atom (init-db (conj (keys column-families) :default)))
     column-families)))


(defmethod ig/init-key :blaze.db.kv/mem
  [_ {:keys [column-families]}]
  (log/info "Open in-memory key-value store.")
  (init-mem-kv-store column-families))
