(ns blaze.db.kv.rocksdb-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.db.kv :as kv]
   [blaze.db.kv-spec]
   [blaze.db.kv.rocksdb :as rocksdb]
   [blaze.db.kv.rocksdb-spec]
   [blaze.db.kv.rocksdb.impl-spec]
   [blaze.db.kv.rocksdb.metrics :as-alias metrics]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu :refer [bytes= given-thrown]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [juxt.iota :refer [given]])
  (:import
   [java.nio.file Files]
   [java.nio.file.attribute FileAttribute]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- ba [& bytes]
  (byte-array bytes))

(defn- bb [& bytes]
  (-> (bb/allocate-direct (count bytes))
      (bb/put-byte-array! (byte-array bytes))
      bb/flip!))

(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {::kv/rocksdb nil})
      :key := ::kv/rocksdb
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {::kv/rocksdb {}})
      :key := ::kv/rocksdb
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :dir))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :stats))))

  (testing "invalid dir"
    (given-thrown (ig/init {::kv/rocksdb {:dir ::invalid}})
      :key := ::kv/rocksdb
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :stats))
      [:explain ::s/problems 1 :pred] := `string?
      [:explain ::s/problems 1 :val] := ::invalid))

  (testing "invalid block-cache"
    (given-thrown (ig/init {::kv/rocksdb {:block-cache ::invalid}})
      :key := ::kv/rocksdb
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :dir))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :stats))
      [:explain ::s/problems 2 :via] := [::rocksdb/block-cache]
      [:explain ::s/problems 2 :val] := ::invalid))

  (testing "invalid stats"
    (given-thrown (ig/init {::kv/rocksdb {:stats ::invalid}})
      :key := ::kv/rocksdb
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :dir))
      [:explain ::s/problems 1 :via] := [::rocksdb/stats]
      [:explain ::s/problems 1 :val] := ::invalid)))

(deftest stats-collector-init-test
  (testing "nil config"
    (given-thrown (ig/init {::rocksdb/stats-collector nil})
      :key := ::rocksdb/stats-collector
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {::rocksdb/stats-collector {}})
      :key := ::rocksdb/stats-collector
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :stats))))

  (testing "invalid stats"
    (given-thrown (ig/init {::rocksdb/stats-collector {:stats ::invalid}})
      :key := ::rocksdb/stats-collector
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :via] := [::metrics/stats]
      [:explain ::s/problems 0 :val] := ::invalid)))

(deftest block-cache-collector-init-test
  (testing "nil config"
    (given-thrown (ig/init {::rocksdb/block-cache-collector nil})
      :key := ::rocksdb/block-cache-collector
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {::rocksdb/block-cache-collector {}})
      :key := ::rocksdb/block-cache-collector
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :block-cache))))

  (testing "invalid stats"
    (given-thrown (ig/init {::rocksdb/block-cache-collector {:block-cache ::invalid}})
      :key := ::rocksdb/block-cache-collector
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :via] := [::rocksdb/block-cache]
      [:explain ::s/problems 0 :val] := ::invalid)))

(deftest table-reader-collector-init-test
  (testing "nil config"
    (given-thrown (ig/init {::rocksdb/table-reader-collector nil})
      :key := ::rocksdb/table-reader-collector
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {::rocksdb/table-reader-collector {}})
      :key := ::rocksdb/table-reader-collector
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :stores))))

  (testing "invalid stores"
    (given-thrown (ig/init {::rocksdb/table-reader-collector {:stores ::invalid}})
      :key := ::rocksdb/table-reader-collector
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :via] := [::metrics/stores]
      [:explain ::s/problems 0 :val] := ::invalid)))

(defn- new-temp-dir! []
  (str (Files/createTempDirectory "blaze" (make-array FileAttribute 0))))

(defn- config [dir]
  {::kv/rocksdb
   {:dir dir
    :block-cache (ig/ref ::rocksdb/block-cache)
    :stats (ig/ref ::rocksdb/stats)}
   ::rocksdb/block-cache {}
   ::rocksdb/env {}
   ::rocksdb/stats {}
   ::rocksdb/stats-collector {:stats {"default" (ig/ref ::rocksdb/stats)}}
   ::rocksdb/block-cache-collector
   {:block-cache (ig/ref ::rocksdb/block-cache)}
   ::rocksdb/table-reader-collector
   {:stores {"default" (ig/ref ::kv/rocksdb)}}})

(deftest valid-test
  (with-system [{db ::kv/rocksdb} (config (new-temp-dir!))]
    (with-open [snapshot (kv/new-snapshot db)
                iter (kv/new-iterator snapshot)]
      (testing "iterator is initially invalid"
        (is (not (kv/valid? iter)))))))

(defmacro with-system-data
  "Runs `body` inside a system that is initialized from `config`, bound to
  `binding-form` and finally halted.

  Additionally the database is initialized with `entries`."
  [[binding-form config] entries & body]
  `(with-system [{db# ::kv/rocksdb :as system#} ~config]
     (kv/put! db# ~entries)
     (let [~binding-form system#] ~@body)))

(deftest seek-to-first-test
  (with-system-data [{db ::kv/rocksdb} (config (new-temp-dir!))]
    [[(ba 0x01) (ba 0x10)]
     [(ba 0x02) (ba 0x20)]]

    (with-open [snapshot (kv/new-snapshot db)
                iter (kv/new-iterator snapshot)]

      (kv/seek-to-first! iter)
      (is (kv/valid? iter))
      (is (bytes= (ba 0x01) (kv/key iter)))
      (is (bytes= (ba 0x10) (kv/value iter))))))

(deftest seek-to-last-test
  (with-system-data [{db ::kv/rocksdb} (config (new-temp-dir!))]
    [[(ba 0x01) (ba 0x10)]
     [(ba 0x02) (ba 0x20)]]

    (with-open [snapshot (kv/new-snapshot db)
                iter (kv/new-iterator snapshot)]

      (kv/seek-to-last! iter)
      (is (kv/valid? iter))
      (is (bytes= (ba 0x02) (kv/key iter)))
      (is (bytes= (ba 0x20) (kv/value iter))))))

(defn- reverse-comparator-config [dir]
  {::kv/rocksdb
   {:dir dir
    :block-cache (ig/ref ::rocksdb/block-cache)
    :stats (ig/ref ::rocksdb/stats)
    :column-families {:a {:reverse-comparator? true}}}
   ::rocksdb/block-cache {}
   ::rocksdb/stats {}})

(deftest seek-test
  (with-system-data [{db ::kv/rocksdb} (config (new-temp-dir!))]
    [[(ba 0x01) (ba 0x10)]
     [(ba 0x03) (ba 0x30)]]

    (with-open [snapshot (kv/new-snapshot db)
                iter (kv/new-iterator snapshot)]

      (testing "before first entry"
        (kv/seek! iter (ba 0x00))
        (is (kv/valid? iter))
        (is (bytes= (ba 0x01) (kv/key iter)))
        (is (bytes= (ba 0x10) (kv/value iter))))

      (testing "at first entry"
        (kv/seek! iter (ba 0x01))
        (is (kv/valid? iter))
        (is (bytes= (ba 0x01) (kv/key iter)))
        (is (bytes= (ba 0x10) (kv/value iter))))

      (testing "before second entry"
        (kv/seek! iter (ba 0x02))
        (is (kv/valid? iter))
        (is (bytes= (ba 0x03) (kv/key iter)))
        (is (bytes= (ba 0x30) (kv/value iter))))

      (testing "at second entry"
        (kv/seek! iter (ba 0x03))
        (is (kv/valid? iter))
        (is (bytes= (ba 0x03) (kv/key iter)))
        (is (bytes= (ba 0x30) (kv/value iter))))

      (testing "overshoot"
        (kv/seek! iter (ba 0x04))
        (is (not (kv/valid? iter))))))

  (testing "reverse comparator"
    (with-system-data [{db ::kv/rocksdb} (reverse-comparator-config (new-temp-dir!))]
      [[:a (ba 0x01) (ba 0x10)]
       [:a (ba 0x03) (ba 0x30)]]

      (with-open [snapshot (kv/new-snapshot db)
                  iter (kv/new-iterator snapshot :a)]

        (testing "before first entry"
          (kv/seek! iter (ba 0x04))
          (is (kv/valid? iter))
          (is (bytes= (ba 0x03) (kv/key iter)))
          (is (bytes= (ba 0x30) (kv/value iter))))

        (testing "at first entry"
          (kv/seek! iter (ba 0x03))
          (is (kv/valid? iter))
          (is (bytes= (ba 0x03) (kv/key iter)))
          (is (bytes= (ba 0x30) (kv/value iter))))

        (testing "before second entry"
          (kv/seek! iter (ba 0x02))
          (is (kv/valid? iter))
          (is (bytes= (ba 0x01) (kv/key iter)))
          (is (bytes= (ba 0x10) (kv/value iter))))

        (testing "at second entry"
          (kv/seek! iter (ba 0x01))
          (is (kv/valid? iter))
          (is (bytes= (ba 0x01) (kv/key iter)))
          (is (bytes= (ba 0x10) (kv/value iter))))

        (testing "overshoot"
          (kv/seek! iter (ba 0x00))
          (is (not (kv/valid? iter))))))))

(deftest seek-buffer-test
  (with-system-data [{db ::kv/rocksdb} (config (new-temp-dir!))]
    [[(ba 0x01) (ba 0x10)]
     [(ba 0x03) (ba 0x30)]]

    (with-open [snapshot (kv/new-snapshot db)
                iter (kv/new-iterator snapshot)]

      (testing "before first entry"
        (kv/seek-buffer! iter (bb 0x00))
        (is (kv/valid? iter))
        (is (bytes= (ba 0x01) (kv/key iter)))
        (is (bytes= (ba 0x10) (kv/value iter))))

      (testing "at first entry"
        (kv/seek-buffer! iter (bb 0x01))
        (is (kv/valid? iter))
        (is (bytes= (ba 0x01) (kv/key iter)))
        (is (bytes= (ba 0x10) (kv/value iter))))

      (testing "before second entry"
        (kv/seek-buffer! iter (bb 0x02))
        (is (kv/valid? iter))
        (is (bytes= (ba 0x03) (kv/key iter)))
        (is (bytes= (ba 0x30) (kv/value iter))))

      (testing "at second entry"
        (kv/seek-buffer! iter (bb 0x03))
        (is (kv/valid? iter))
        (is (bytes= (ba 0x03) (kv/key iter)))
        (is (bytes= (ba 0x30) (kv/value iter))))

      (testing "overshoot"
        (kv/seek-buffer! iter (bb 0x04))
        (is (not (kv/valid? iter))))))

  (testing "reverse comparator"
    (with-system-data [{db ::kv/rocksdb} (reverse-comparator-config (new-temp-dir!))]
      [[:a (ba 0x01) (ba 0x10)]
       [:a (ba 0x03) (ba 0x30)]]

      (with-open [snapshot (kv/new-snapshot db)
                  iter (kv/new-iterator snapshot :a)]

        (testing "before first entry"
          (kv/seek-buffer! iter (bb 0x04))
          (is (kv/valid? iter))
          (is (bytes= (ba 0x03) (kv/key iter)))
          (is (bytes= (ba 0x30) (kv/value iter))))

        (testing "at first entry"
          (kv/seek-buffer! iter (bb 0x03))
          (is (kv/valid? iter))
          (is (bytes= (ba 0x03) (kv/key iter)))
          (is (bytes= (ba 0x30) (kv/value iter))))

        (testing "before second entry"
          (kv/seek-buffer! iter (bb 0x02))
          (is (kv/valid? iter))
          (is (bytes= (ba 0x01) (kv/key iter)))
          (is (bytes= (ba 0x10) (kv/value iter))))

        (testing "at second entry"
          (kv/seek-buffer! iter (bb 0x01))
          (is (kv/valid? iter))
          (is (bytes= (ba 0x01) (kv/key iter)))
          (is (bytes= (ba 0x10) (kv/value iter))))

        (testing "overshoot"
          (kv/seek-buffer! iter (bb 0x00))
          (is (not (kv/valid? iter))))))))

(deftest seek-for-prev-test
  (with-system-data [{db ::kv/rocksdb} (config (new-temp-dir!))]
    [[(ba 0x01) (ba 0x10)]
     [(ba 0x03) (ba 0x30)]]

    (with-open [snapshot (kv/new-snapshot db)
                iter (kv/new-iterator snapshot)]

      (testing "past second entry"
        (kv/seek-for-prev! iter (ba 0x04))
        (is (kv/valid? iter))
        (is (bytes= (ba 0x03) (kv/key iter)))
        (is (bytes= (ba 0x30) (kv/value iter))))

      (testing "at second entry"
        (kv/seek-for-prev! iter (ba 0x03))
        (is (kv/valid? iter))
        (is (bytes= (ba 0x03) (kv/key iter)))
        (is (bytes= (ba 0x30) (kv/value iter))))

      (testing "past first entry"
        (kv/seek-for-prev! iter (ba 0x02))
        (is (kv/valid? iter))
        (is (bytes= (ba 0x01) (kv/key iter)))
        (is (bytes= (ba 0x10) (kv/value iter))))

      (testing "at first entry"
        (kv/seek-for-prev! iter (ba 0x01))
        (is (kv/valid? iter))
        (is (bytes= (ba 0x01) (kv/key iter)))
        (is (bytes= (ba 0x10) (kv/value iter))))

      (testing "overshoot"
        (kv/seek-for-prev! iter (ba 0x00))
        (is (not (kv/valid? iter)))))))

(deftest next-test
  (with-system-data [{db ::kv/rocksdb} (config (new-temp-dir!))]
    [[(ba 0x01) (ba 0x10)]
     [(ba 0x03) (ba 0x30)]]

    (with-open [snapshot (kv/new-snapshot db)
                iter (kv/new-iterator snapshot)]

      (testing "first entry"
        (kv/seek-to-first! iter)
        (is (kv/valid? iter))
        (is (bytes= (ba 0x01) (kv/key iter)))
        (is (bytes= (ba 0x10) (kv/value iter))))

      (testing "second entry"
        (kv/next! iter)
        (is (kv/valid? iter))
        (is (bytes= (ba 0x03) (kv/key iter)))
        (is (bytes= (ba 0x30) (kv/value iter))))

      (testing "end"
        (kv/next! iter)
        (is (not (kv/valid? iter)))))))

(deftest prev-test
  (with-system-data [{db ::kv/rocksdb} (config (new-temp-dir!))]
    [[(ba 0x01) (ba 0x10)]
     [(ba 0x03) (ba 0x30)]]

    (with-open [snapshot (kv/new-snapshot db)
                iter (kv/new-iterator snapshot)]

      (testing "first entry"
        (kv/seek-to-last! iter)
        (is (kv/valid? iter))
        (is (bytes= (ba 0x03) (kv/key iter)))
        (is (bytes= (ba 0x30) (kv/value iter))))

      (testing "second entry"
        (kv/prev! iter)
        (is (kv/valid? iter))
        (is (bytes= (ba 0x01) (kv/key iter)))
        (is (bytes= (ba 0x10) (kv/value iter))))

      (testing "end"
        (kv/prev! iter)
        (is (not (kv/valid? iter)))))))

(deftest key-test
  (with-system-data [{db ::kv/rocksdb} (config (new-temp-dir!))]
    [[(ba 0x01 0x02) (ba 0x00)]]

    (with-open [snapshot (kv/new-snapshot db)
                iter (kv/new-iterator snapshot)]

      (testing "puts the first byte into the buffer without overflowing"
        (kv/seek-to-first! iter)
        (let [buf (bb/allocate-direct 1)]
          (is (= 2 (kv/key! iter buf)))
          (is (= 0x01 (bb/get-byte! buf)))))

      (testing "sets the limit of a bigger buffer to two"
        (kv/seek-to-first! iter)
        (let [buf (bb/allocate-direct 3)]
          (is (= 2 (kv/key! iter buf)))
          (is (= 2 (bb/limit buf)))))

      (testing "writes the key at position"
        (kv/seek-to-first! iter)
        (let [buf (bb/allocate-direct 3)]
          (bb/set-position! buf 1)
          (is (= 2 (kv/key! iter buf)))
          (is (= 1 (bb/position buf)))
          (is (= 3 (bb/limit buf)))
          (is (= 0x00 (bb/get-byte! buf 0)))
          (is (= 0x01 (bb/get-byte! buf)))
          (is (= 0x02 (bb/get-byte! buf))))))))

(deftest value-test
  (with-system-data [{db ::kv/rocksdb} (config (new-temp-dir!))]
    [[(ba 0x00) (ba 0x01 0x02)]]

    (with-open [snapshot (kv/new-snapshot db)
                iter (kv/new-iterator snapshot)]

      (testing "puts the first byte into the buffer without overflowing"
        (kv/seek-to-first! iter)
        (let [buf (bb/allocate-direct 1)]
          (is (= 2 (kv/value! iter buf)))
          (is (= 0x01 (bb/get-byte! buf)))))

      (testing "sets the limit of a bigger buffer to two"
        (kv/seek-to-first! iter)
        (let [buf (bb/allocate-direct 3)]
          (is (= 2 (kv/value! iter buf)))
          (is (= 2 (bb/limit buf)))))

      (testing "writes the value at position"
        (kv/seek-to-first! iter)
        (let [buf (bb/allocate-direct 3)]
          (bb/set-position! buf 1)
          (is (= 2 (kv/value! iter buf)))
          (is (= 1 (bb/position buf)))
          (is (= 3 (bb/limit buf)))
          (is (= 0x00 (bb/get-byte! buf 0)))
          (is (= 0x01 (bb/get-byte! buf)))
          (is (= 0x02 (bb/get-byte! buf))))))))

(defn- a-b-config [dir]
  {::kv/rocksdb
   {:dir dir
    :block-cache (ig/ref ::rocksdb/block-cache)
    :stats (ig/ref ::rocksdb/stats)
    :column-families {:a nil :b nil}}
   ::rocksdb/block-cache {}
   ::rocksdb/stats {}})

(deftest different-column-families-test
  (with-system-data [{db ::kv/rocksdb} (a-b-config (new-temp-dir!))]
    [[:a (ba 0x00) (ba 0x01)]
     [:b (ba 0x00) (ba 0x02)]]

    (with-open [snapshot (kv/new-snapshot db)
                iter-a (kv/new-iterator snapshot :a)
                iter-b (kv/new-iterator snapshot :b)]

      (testing "the value in :a is 0x01"
        (kv/seek-to-first! iter-a)
        (is (bytes= (ba 0x01) (kv/value iter-a))))

      (testing "the value in :b is 0x02"
        (kv/seek-to-first! iter-b)
        (is (bytes= (ba 0x02) (kv/value iter-b))))

      (testing "column-family :c doesn't exist"
        (is (ba/not-found? (ba/try-anomaly (kv/new-iterator snapshot :c))))))))

(defn- a-config [dir]
  {::kv/rocksdb
   {:dir dir
    :block-cache (ig/ref ::rocksdb/block-cache)
    :stats (ig/ref ::rocksdb/stats)
    :column-families {:a nil}}
   ::rocksdb/block-cache {}
   ::rocksdb/stats {}})

(deftest snapshot-get-test
  (with-system-data [{db ::kv/rocksdb} (a-config (new-temp-dir!))]
    [[(ba 0x00) (ba 0x01)]
     [:a (ba 0x00) (ba 0x02)]]

    (with-open [snapshot (kv/new-snapshot db)]

      (testing "returns found value"
        (is (bytes= (ba 0x01) (kv/snapshot-get snapshot (ba 0x00)))))

      (testing "returns nil on not found value"
        (is (nil? (kv/snapshot-get snapshot (ba 0x01)))))

      (testing "returns found value of column-family :a"
        (is (bytes= (ba 0x02) (kv/snapshot-get snapshot :a (ba 0x00)))))

      (testing "returns nil on not found value of column-family :a"
        (is (nil? (kv/snapshot-get snapshot :a (ba 0x01))))))))

(deftest get-test
  (with-system-data [{db ::kv/rocksdb} (a-config (new-temp-dir!))]
    [[(ba 0x00) (ba 0x01)]
     [:a (ba 0x00) (ba 0x02)]]

    (testing "returns found value"
      (is (bytes= (ba 0x01) (kv/get db (ba 0x00)))))

    (testing "returns nil on not found value"
      (is (nil? (kv/get db (ba 0x01)))))

    (testing "returns found value of column-family :a"
      (is (bytes= (ba 0x02) (kv/get db :a (ba 0x00)))))

    (testing "returns nil on not found value of column-family :a"
      (is (nil? (kv/get db :a (ba 0x01)))))))

(deftest multi-get-test
  (with-system-data [{db ::kv/rocksdb} (config (new-temp-dir!))]
    [[(ba 0x00) (ba 0x10)]
     [(ba 0x01) (ba 0x11)]]

    (testing "returns all found entries"
      (let [m (into
               {}
               (map (fn [[k v]] [(vec k) (vec v)]))
               (kv/multi-get db [(ba 0x00) (ba 0x01) (ba 0x02)]))]
        (is (= [0x10] (get m [0x00])))
        (is (= [0x11] (get m [0x01])))))))

(deftest put-test
  (with-system [{db ::kv/rocksdb} (config (new-temp-dir!))]

    (testing "key value"
      (kv/put! db (ba 0x00) (ba 0x01))
      (is (bytes= (ba 0x01) (kv/get db (ba 0x00)))))

    (testing "entries"
      (kv/put! db [[:default (ba 0x00) (ba 0x01)]])
      (is (bytes= (ba 0x01) (kv/get db (ba 0x00)))))

    (testing "errors on unknown column-family"
      (is (ba/not-found? (ba/try-anomaly (kv/put! db [[:a (ba 0x00) (ba 0x01)]])))))))

(deftest delete-test
  (with-system-data [{db ::kv/rocksdb} (config (new-temp-dir!))]
    [[(ba 0x00) (ba 0x10)]]

    (kv/delete! db [(ba 0x00)])

    (is (nil? (kv/get db (ba 0x00))))))

(defn- merge-config [dir]
  {::kv/rocksdb
   {:dir dir
    :block-cache (ig/ref ::rocksdb/block-cache)
    :stats (ig/ref ::rocksdb/stats)
    :column-families {:default {:merge-operator :stringappend}}}
   ::rocksdb/block-cache {}
   ::rocksdb/stats {}})

(defn- merge-a-config [dir]
  {::kv/rocksdb
   {:dir dir
    :block-cache (ig/ref ::rocksdb/block-cache)
    :stats (ig/ref ::rocksdb/stats)
    :column-families {:a {:merge-operator :stringappend}}}
   ::rocksdb/block-cache {}
   ::rocksdb/stats {}})

(deftest write-test
  (testing "default column-family"
    (with-system-data [{db ::kv/rocksdb} (merge-config (new-temp-dir!))]
      [[(ba 0x00) (ba 0x10)]]

      (testing "put"
        (kv/write! db [[:put (ba 0x01) (ba 0x11)]])
        (is (bytes= (ba 0x11) (kv/get db (ba 0x01)))))

      (testing "merge"
        (kv/write! db [[:merge (ba 0x00) (ba 0x20)]])
        ;; 0x2C is a comma
        (is (bytes= (ba 0x10 0x2C 0x20) (kv/get db (ba 0x00)))))

      (testing "delete"
        (kv/write! db [[:delete (ba 0x00)]])
        (is (nil? (kv/get db (ba 0x00)))))))

  (testing "custom column-family"
    (with-system-data [{db ::kv/rocksdb} (merge-a-config (new-temp-dir!))]
      [[:a (ba 0x00) (ba 0x10)]]

      (testing "put"
        (kv/write! db [[:put :a (ba 0x01) (ba 0x11)]])
        (is (bytes= (ba 0x11) (kv/get db :a (ba 0x01)))))

      (testing "merge"
        (kv/write! db [[:merge :a (ba 0x00) (ba 0x20)]])
        ;; 0x2C is a comma
        (is (bytes= (ba 0x10 0x2C 0x20) (kv/get db :a (ba 0x00)))))

      (testing "delete"
        (kv/write! db [[:delete :a (ba 0x00)]])
        (is (nil? (kv/get db :a (ba 0x00))))))))

(deftest column-families-test
  (with-system [{db ::kv/rocksdb} (config (new-temp-dir!))]

    (is (= [:default] (rocksdb/column-families db))))

  (with-system [{db ::kv/rocksdb} (a-config (new-temp-dir!))]

    (is (= [:default :a] (rocksdb/column-families db)))))

(deftest get-property-test
  (with-system [{db ::kv/rocksdb} (config (new-temp-dir!))]

    (is (= "0" (rocksdb/get-property db "rocksdb.num-files-at-level0")))
    (is (= "0" (rocksdb/get-property db "rocksdb.num-files-at-level1")))

    (is (zero? (rocksdb/get-long-property db "rocksdb.estimate-table-readers-mem")))

    (is (string? (rocksdb/get-property db "rocksdb.stats")))

    (is (string? (rocksdb/get-property db "rocksdb.sstables")))

    (testing "not-found"
      (doseq [fn [rocksdb/get-property rocksdb/get-long-property]]
        (given (fn db "name-143100")
          ::anom/category := ::anom/not-found
          ::anom/message := "Property with name `name-143100` was not found."))))

  (testing "with column-family"
    (with-system [{db ::kv/rocksdb} (a-config (new-temp-dir!))]

      (is (= "0" (rocksdb/get-property db :a "rocksdb.num-files-at-level0")))
      (is (= "0" (rocksdb/get-property db :a "rocksdb.num-files-at-level1")))

      (is (zero? (rocksdb/get-long-property db :a "rocksdb.estimate-table-readers-mem")))

      (is (string? (rocksdb/get-property db :a "rocksdb.stats")))

      (is (string? (rocksdb/get-property db :a "rocksdb.sstables")))

      (testing "not-found"
        (doseq [fn [rocksdb/get-property rocksdb/get-long-property]]
          (given (fn db :a "name-143127")
            ::anom/category := ::anom/not-found
            ::anom/message := "Property with name `name-143127` was not found on column-family with name `a`."))))))

(deftest table-properties-test
  (testing "default column-family"
    (with-system [{db ::kv/rocksdb} (config (new-temp-dir!))]
      (run!
       (fn [i]
         (kv/put!
          db
          (bs/to-byte-array (bs/from-hex (str/upper-case (Long/toHexString i))))
          (apply ba (range 10000))))
       (range 10000 20000))

      (given (rocksdb/table-properties db)
        count := 1
        [0 :comparator-name] := "leveldb.BytewiseComparator"
        [0 :compression-name] := "LZ4"
        [0 :data-size] := 2168082
        [0 :index-size] := 86351
        [0 :num-data-blocks] := 6631
        [0 :num-entries] := 6631
        [0 :top-level-index-size] := 0
        [0 :total-raw-key-size] := 66310
        [0 :total-raw-value-size] := 66310000)))

  (testing "with column-family"
    (with-system [{db ::kv/rocksdb} (a-config (new-temp-dir!))]
      (run!
       (fn [i]
         (kv/put!
          db
          [[:a
            (bs/to-byte-array (bs/from-hex (str/upper-case (Long/toHexString i))))
            (apply ba (range 10000))]]))
       (range 10000 20000))

      (given (rocksdb/table-properties db :a)
        count := 1
        [0 :comparator-name] := "leveldb.BytewiseComparator"
        [0 :compression-name] := "LZ4"
        [0 :data-size] := 2168082
        [0 :index-size] := 86351
        [0 :num-data-blocks] := 6631
        [0 :num-entries] := 6631
        [0 :top-level-index-size] := 0
        [0 :total-raw-key-size] := 66310
        [0 :total-raw-value-size] := 66310000)))

  (testing "with unknown column-family"
    (with-system [{db ::kv/rocksdb} (config (new-temp-dir!))]
      (given (rocksdb/table-properties db :column-family-143119)
        ::anom/category := ::anom/not-found
        ::anom/message := "column family `column-family-143119` not found"))))

(deftest compact-range-test
  (with-system-data [{db ::kv/rocksdb} (config (new-temp-dir!))]
    [[(ba 0x00) (ba 0x10)]]

    (rocksdb/compact-range! db)

    (is (bytes= (ba 0x10) (kv/get db (ba 0x00)))))

  (testing "with column-family"
    (with-system-data [{db ::kv/rocksdb} (a-config (new-temp-dir!))]
      [[:a (ba 0x00) (ba 0x10)]]

      (rocksdb/compact-range! db :a true 1)

      (is (bytes= (ba 0x10) (kv/get db :a (ba 0x00)))))))
