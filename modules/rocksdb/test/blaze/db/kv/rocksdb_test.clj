(ns blaze.db.kv.rocksdb-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.db.kv :as kv]
   [blaze.db.kv-spec]
   [blaze.db.kv.rocksdb :as rocksdb]
   [blaze.db.kv.rocksdb-spec]
   [blaze.db.kv.rocksdb.column-family-meta-data :as-alias column-family-meta-data]
   [blaze.db.kv.rocksdb.column-family-meta-data.level :as-alias column-family-meta-data-level]
   [blaze.db.kv.rocksdb.impl-spec]
   [blaze.db.kv.rocksdb.metrics :as-alias metrics]
   [blaze.db.kv.rocksdb.spec]
   [blaze.module.test-util :refer [given-failed-future given-failed-system with-system]]
   [blaze.test-util :as tu :refer [ba bb bytes=]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [java.nio.file Files]
   [java.nio.file.attribute FileAttribute]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

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

(deftest init-test
  (testing "nil config"
    (given-failed-system {::kv/rocksdb nil}
      :key := ::kv/rocksdb
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {::kv/rocksdb {}}
      :key := ::kv/rocksdb
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :dir))))

  (testing "invalid dir"
    (given-failed-system {::kv/rocksdb {:dir ::invalid}}
      :key := ::kv/rocksdb
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::rocksdb/dir]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid block-cache"
    (given-failed-system (assoc-in (config (new-temp-dir!)) [::kv/rocksdb :block-cache] ::invalid)
      :key := ::kv/rocksdb
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::rocksdb/block-cache]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid stats"
    (given-failed-system (assoc-in (config (new-temp-dir!)) [::kv/rocksdb :stats] ::invalid)
      :key := ::kv/rocksdb
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::rocksdb/stats]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(deftest stats-collector-init-test
  (testing "nil config"
    (given-failed-system {::rocksdb/stats-collector nil}
      :key := ::rocksdb/stats-collector
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {::rocksdb/stats-collector {}}
      :key := ::rocksdb/stats-collector
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :stats))))

  (testing "invalid stats"
    (given-failed-system {::rocksdb/stats-collector {:stats ::invalid}}
      :key := ::rocksdb/stats-collector
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::metrics/stats]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(deftest block-cache-collector-init-test
  (testing "nil config"
    (given-failed-system {::rocksdb/block-cache-collector nil}
      :key := ::rocksdb/block-cache-collector
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {::rocksdb/block-cache-collector {}}
      :key := ::rocksdb/block-cache-collector
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :block-cache))))

  (testing "invalid stats"
    (given-failed-system {::rocksdb/block-cache-collector {:block-cache ::invalid}}
      :key := ::rocksdb/block-cache-collector
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::rocksdb/block-cache]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(deftest table-reader-collector-init-test
  (testing "nil config"
    (given-failed-system {::rocksdb/table-reader-collector nil}
      :key := ::rocksdb/table-reader-collector
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {::rocksdb/table-reader-collector {}}
      :key := ::rocksdb/table-reader-collector
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :stores))))

  (testing "invalid stores"
    (given-failed-system {::rocksdb/table-reader-collector {:stores ::invalid}}
      :key := ::rocksdb/table-reader-collector
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::metrics/stores]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(deftest valid-test
  (with-system [{db ::kv/rocksdb} (config (new-temp-dir!))]
    (with-open [snapshot (kv/new-snapshot db)
                iter (kv/new-iterator snapshot :default)]
      (testing "iterator is initially invalid"
        (is (not (kv/valid? iter)))))))

(defmacro with-system-data
  "Runs `body` inside a system that is initialized from `config`.

  Additionally the database is initialized with `entries`."
  [[binding-form config] entries & body]
  `(with-system [{db# ::kv/rocksdb :as system#} ~config]
     (kv/put! db# ~entries)
     (let [~binding-form system#] ~@body)))

(deftest seek-to-first-test
  (with-system-data [{db ::kv/rocksdb} (config (new-temp-dir!))]
    [[:default (ba 0x01) (ba 0x10)]
     [:default (ba 0x02) (ba 0x20)]]

    (with-open [snapshot (kv/new-snapshot db)
                iter (kv/new-iterator snapshot :default)]

      (kv/seek-to-first! iter)
      (is (kv/valid? iter))
      (is (bytes= (ba 0x01) (kv/key iter)))
      (is (bytes= (ba 0x10) (kv/value iter))))))

(deftest seek-to-last-test
  (with-system-data [{db ::kv/rocksdb} (config (new-temp-dir!))]
    [[:default (ba 0x01) (ba 0x10)]
     [:default (ba 0x02) (ba 0x20)]]

    (with-open [snapshot (kv/new-snapshot db)
                iter (kv/new-iterator snapshot :default)]

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
    [[:default (ba 0x01) (ba 0x10)]
     [:default (ba 0x03) (ba 0x30)]]

    (with-open [snapshot (kv/new-snapshot db)
                iter (kv/new-iterator snapshot :default)]

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
    [[:default (ba 0x01) (ba 0x10)]
     [:default (ba 0x03) (ba 0x30)]]

    (with-open [snapshot (kv/new-snapshot db)
                iter (kv/new-iterator snapshot :default)]

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
    [[:default (ba 0x01) (ba 0x10)]
     [:default (ba 0x03) (ba 0x30)]]

    (with-open [snapshot (kv/new-snapshot db)
                iter (kv/new-iterator snapshot :default)]

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

(deftest seek-for-prev-buffer-test
  (with-system-data [{db ::kv/rocksdb} (config (new-temp-dir!))]
    [[:default (ba 0x01) (ba 0x10)]
     [:default (ba 0x03) (ba 0x30)]]

    (with-open [snapshot (kv/new-snapshot db)
                iter (kv/new-iterator snapshot :default)]

      (testing "past second entry"
        (kv/seek-for-prev-buffer! iter (bb 0x04))
        (is (kv/valid? iter))
        (is (bytes= (ba 0x03) (kv/key iter)))
        (is (bytes= (ba 0x30) (kv/value iter))))

      (testing "at second entry"
        (kv/seek-for-prev-buffer! iter (bb 0x03))
        (is (kv/valid? iter))
        (is (bytes= (ba 0x03) (kv/key iter)))
        (is (bytes= (ba 0x30) (kv/value iter))))

      (testing "past first entry"
        (kv/seek-for-prev-buffer! iter (bb 0x02))
        (is (kv/valid? iter))
        (is (bytes= (ba 0x01) (kv/key iter)))
        (is (bytes= (ba 0x10) (kv/value iter))))

      (testing "at first entry"
        (kv/seek-for-prev-buffer! iter (bb 0x01))
        (is (kv/valid? iter))
        (is (bytes= (ba 0x01) (kv/key iter)))
        (is (bytes= (ba 0x10) (kv/value iter))))

      (testing "overshoot"
        (kv/seek-for-prev-buffer! iter (bb 0x00))
        (is (not (kv/valid? iter)))))))

(deftest next-test
  (with-system-data [{db ::kv/rocksdb} (config (new-temp-dir!))]
    [[:default (ba 0x01) (ba 0x10)]
     [:default (ba 0x03) (ba 0x30)]]

    (with-open [snapshot (kv/new-snapshot db)
                iter (kv/new-iterator snapshot :default)]

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
    [[:default (ba 0x01) (ba 0x10)]
     [:default (ba 0x03) (ba 0x30)]]

    (with-open [snapshot (kv/new-snapshot db)
                iter (kv/new-iterator snapshot :default)]

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
    [[:default (ba 0x01 0x02) (ba 0x00)]]

    (with-open [snapshot (kv/new-snapshot db)
                iter (kv/new-iterator snapshot :default)]

      (testing "puts the first byte into the buffer without overflowing"
        (kv/seek-to-first! iter)
        (let [buf (bb/allocate 1)]
          (is (= 2 (kv/key! iter buf)))
          (is (= 0x01 (bb/get-byte! buf)))))

      (testing "sets the limit of a bigger buffer to two"
        (kv/seek-to-first! iter)
        (let [buf (bb/allocate 3)]
          (is (= 2 (kv/key! iter buf)))
          (is (= 2 (bb/limit buf)))))

      (testing "writes the key at position"
        (kv/seek-to-first! iter)
        (let [buf (bb/allocate 3)]
          (bb/set-position! buf 1)
          (is (= 2 (kv/key! iter buf)))
          (is (= 1 (bb/position buf)))
          (is (= 3 (bb/limit buf)))
          (is (= 0x00 (bb/get-byte! buf 0)))
          (is (= 0x01 (bb/get-byte! buf)))
          (is (= 0x02 (bb/get-byte! buf))))))))

(deftest value-test
  (with-system-data [{db ::kv/rocksdb} (config (new-temp-dir!))]
    [[:default (ba 0x00) (ba 0x01 0x02)]]

    (with-open [snapshot (kv/new-snapshot db)
                iter (kv/new-iterator snapshot :default)]

      (testing "puts the first byte into the buffer without overflowing"
        (kv/seek-to-first! iter)
        (let [buf (bb/allocate 1)]
          (is (= 2 (kv/value! iter buf)))
          (is (= 0x01 (bb/get-byte! buf)))))

      (testing "sets the limit of a bigger buffer to two"
        (kv/seek-to-first! iter)
        (let [buf (bb/allocate 3)]
          (is (= 2 (kv/value! iter buf)))
          (is (= 2 (bb/limit buf)))))

      (testing "writes the value at position"
        (kv/seek-to-first! iter)
        (let [buf (bb/allocate 3)]
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
  (with-system-data [{db ::kv/rocksdb} (config (new-temp-dir!))]
    [[:default (ba 0x00) (ba 0x01)]]

    (with-open [snapshot (kv/new-snapshot db)]

      (testing "returns found value"
        (is (bytes= (ba 0x01) (kv/snapshot-get snapshot :default (ba 0x00)))))

      (testing "returns nil on not found value"
        (is (nil? (kv/snapshot-get snapshot :default (ba 0x01))))))))

(deftest get-test
  (with-system-data [{db ::kv/rocksdb} (config (new-temp-dir!))]
    [[:default (ba 0x00) (ba 0x01)]]

    (testing "returns found value"
      (is (bytes= (ba 0x01) (kv/get db :default (ba 0x00)))))

    (testing "returns nil on not found value"
      (is (nil? (kv/get db :default (ba 0x01)))))))

(deftest put-test
  (with-system [{db ::kv/rocksdb} (config (new-temp-dir!))]

    (testing "get after put"
      (kv/put! db [[:default (ba 0x00) (ba 0x01)]])
      (is (bytes= (ba 0x01) (kv/get db :default (ba 0x00)))))

    (testing "errors on unknown column-family"
      (is (ba/not-found? (ba/try-anomaly (kv/put! db [[:a (ba 0x00) (ba 0x01)]])))))))

(deftest delete-test
  (with-system-data [{db ::kv/rocksdb} (config (new-temp-dir!))]
    [[:default (ba 0x00) (ba 0x10)]]

    (kv/delete! db [[:default (ba 0x00)]])

    (is (nil? (kv/get db :default (ba 0x00))))))

(defn- merge-config [dir]
  {::kv/rocksdb
   {:dir dir
    :block-cache (ig/ref ::rocksdb/block-cache)
    :stats (ig/ref ::rocksdb/stats)
    :column-families {:default {:merge-operator :stringappend}}}
   ::rocksdb/block-cache {}
   ::rocksdb/stats {}})

(deftest write-test
  (with-system-data [{db ::kv/rocksdb} (merge-config (new-temp-dir!))]
    [[:default (ba 0x00) (ba 0x10)]]

    (testing "put"
      (kv/write! db [[:put :default (ba 0x01) (ba 0x11)]])
      (is (bytes= (ba 0x11) (kv/get db :default (ba 0x01)))))

    (testing "merge"
      (kv/write! db [[:merge :default (ba 0x00) (ba 0x20)]])
      ;; 0x2C is a comma
      (is (bytes= (ba 0x10 0x2C 0x20) (kv/get db :default (ba 0x00)))))

    (testing "delete"
      (kv/write! db [[:delete :default (ba 0x00)]])
      (is (nil? (kv/get db :default (ba 0x00)))))))

(deftest estimate-num-keys-test
  (with-system [{db ::kv/rocksdb} (config (new-temp-dir!))]
    (is (zero? (kv/estimate-num-keys db :default)))

    (given (kv/estimate-num-keys db :foo)
      ::anom/category := ::anom/not-found
      ::anom/message := "Column family `foo` not found."))

  (with-system-data [{db ::kv/rocksdb} (config (new-temp-dir!))]
    [[:default (ba 0x00) (ba 0x10)]]

    (is (= 1 (kv/estimate-num-keys db :default)))))

(def ^:private full-key-range
  [#blaze/byte-string"00" #blaze/byte-string"FF"])

(defn int-ba [i]
  (bs/to-byte-array (bs/from-hex (str/upper-case (Long/toHexString i)))))

(deftest estimate-storage-scan-test
  (with-system [{db ::kv/rocksdb} (config (new-temp-dir!))]
    (is (zero? (kv/estimate-scan-size db :default full-key-range)))

    (given (kv/estimate-scan-size db :foo full-key-range)
      ::anom/category := ::anom/not-found
      ::anom/message := "Column family `foo` not found."))

  (with-system [{db ::kv/rocksdb} (config (new-temp-dir!))]
    (run!
     #(kv/put! db [[:default (int-ba %) (apply ba (range 10000))]])
     (range 10000 20000))

    (is (pos? (kv/estimate-scan-size db :default full-key-range)))))

(deftest compact-test
  (with-system [{db ::kv/rocksdb} (config (new-temp-dir!))]
    (is (nil? @(kv/compact! db :default)))

    (given-failed-future (kv/compact! db :foo)
      ::anom/category := ::anom/not-found
      ::anom/message := "Column family `foo` not found."))

  (with-system [{db ::kv/rocksdb} (a-config (new-temp-dir!))]
    (run!
     #(kv/put! db [[:a (int-ba %) (apply ba (range 10000))]])
     (range 10000 20000))

    @(kv/compact! db :a)

    (is (some? (kv/get db :a (ba 0x27 0x10))))))

(deftest path-test
  (with-system [{db ::kv/rocksdb} (config (new-temp-dir!))]
    (is (string? (rocksdb/path db)))))

(deftest column-families-test
  (with-system [{db ::kv/rocksdb} (config (new-temp-dir!))]

    (is (= [:default] (rocksdb/column-families db))))

  (with-system [{db ::kv/rocksdb} (a-config (new-temp-dir!))]

    (is (= [:a :default] (rocksdb/column-families db)))))

(deftest property-test
  (with-system [{db ::kv/rocksdb} (config (new-temp-dir!))]

    (is (= "0" (rocksdb/property db "rocksdb.num-files-at-level0")))
    (is (= "0" (rocksdb/property db "rocksdb.num-files-at-level1")))

    (is (zero? (rocksdb/long-property db "rocksdb.estimate-table-readers-mem")))
    (is (zero? (rocksdb/long-property db "rocksdb.estimate-live-data-size")))

    (is (string? (rocksdb/property db "rocksdb.stats")))

    (is (string? (rocksdb/property db "rocksdb.sstables")))

    (given (rocksdb/map-property db "rocksdb.aggregated-table-properties")
      :data-size := "0"
      :fast-compression-estimated-data-size := "0"
      :filter-size := "0"
      :index-partitions := "0"
      :index-size := "0"
      :num-data-blocks := "0"
      :num-deletions := "0"
      :num-entries := "0"
      :num-filter-entries := "0"
      :num-merge-operands := "0"
      :num-range-deletions := "0"
      :raw-key-size := "0"
      :raw-value-size := "0"
      :slow-compression-estimated-data-size := "0"
      :top-level-index-size := "0")

    (testing "not-found"
      (doseq [fn [rocksdb/property rocksdb/long-property
                  rocksdb/agg-long-property rocksdb/map-property]]
        (given (fn db "name-143100")
          ::anom/category := ::anom/not-found
          ::anom/message := "Property with name `name-143100` was not found."))))

  (testing "with column-family"
    (with-system [{db ::kv/rocksdb} (a-config (new-temp-dir!))]

      (is (= "0" (rocksdb/property db :a "rocksdb.num-files-at-level0")))
      (is (= "0" (rocksdb/property db :a "rocksdb.num-files-at-level1")))

      (is (zero? (rocksdb/long-property db :a "rocksdb.estimate-table-readers-mem")))

      (is (string? (rocksdb/property db :a "rocksdb.stats")))

      (is (string? (rocksdb/property db :a "rocksdb.sstables")))

      (given (rocksdb/map-property db :a "rocksdb.aggregated-table-properties")
        :data-size := "0"
        :fast-compression-estimated-data-size := "0"
        :filter-size := "0"
        :index-partitions := "0"
        :index-size := "0"
        :num-data-blocks := "0"
        :num-deletions := "0"
        :num-entries := "0"
        :num-filter-entries := "0"
        :num-merge-operands := "0"
        :num-range-deletions := "0"
        :raw-key-size := "0"
        :raw-value-size := "0"
        :slow-compression-estimated-data-size := "0"
        :top-level-index-size := "0")

      (testing "column family not found"
        (doseq [fn [rocksdb/property rocksdb/long-property rocksdb/map-property]]
          (given (fn db :foo "rocksdb.stats")
            ::anom/category := ::anom/not-found
            ::anom/message := "Column family `foo` not found.")))

      (testing "property not found"
        (doseq [fn [rocksdb/property rocksdb/long-property rocksdb/map-property]]
          (given (fn db :a "name-143127")
            ::anom/category := ::anom/not-found
            ::anom/message := "Property with name `name-143127` was not found on column-family with name `a`."))))))

(deftest tables-test
  (testing "default column-family"
    (with-system [{db ::kv/rocksdb} (config (new-temp-dir!))]
      (run!
       #(kv/put! db [[:default (int-ba %) (apply ba (range 10000))]])
       (range 10000 20000))

      (Thread/sleep 1000)

      (is (pos-int? (rocksdb/long-property db "rocksdb.estimate-live-data-size")))
      (is (pos-int? (rocksdb/agg-long-property db "rocksdb.estimate-live-data-size")))

      (given (rocksdb/tables db)
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
       #(kv/put! db [[:a (int-ba %) (apply ba (range 10000))]])
       (range 10000 20000))

      (Thread/sleep 1000)

      (is (zero? (rocksdb/long-property db :default "rocksdb.estimate-live-data-size")))
      (is (pos-int? (rocksdb/long-property db :a "rocksdb.estimate-live-data-size")))
      (is (pos-int? (rocksdb/agg-long-property db "rocksdb.estimate-live-data-size")))

      (given (rocksdb/tables db :a)
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
      (given (rocksdb/tables db :column-family-143119)
        ::anom/category := ::anom/not-found
        ::anom/message := "Column family `column-family-143119` not found."))))

(deftest column-family-meta-data-test
  (testing "default column-family"
    (with-system [{db ::kv/rocksdb} (config (new-temp-dir!))]
      (let [meta-data (rocksdb/column-family-meta-data db :default)]
        (given meta-data
          ::column-family-meta-data/name := "default"
          ::column-family-meta-data/file-size := 0
          ::column-family-meta-data/num-files := 0
          [::column-family-meta-data/levels count] := 7)

        (doseq [[idx level] (map-indexed vector (::column-family-meta-data/levels meta-data))]
          (given level
            ::column-family-meta-data-level/level := idx
            ::column-family-meta-data-level/file-size := 0
            ::column-family-meta-data-level/num-files := 0)))))

  (testing "with unknown column-family"
    (with-system [{db ::kv/rocksdb} (config (new-temp-dir!))]
      (given (rocksdb/column-family-meta-data db :column-family-173005)
        ::anom/category := ::anom/not-found
        ::anom/message := "Column family `column-family-173005` not found."))))

(deftest drop-column-family-test
  (let [dir (new-temp-dir!)]
    (testing "open a DB with an :a and a :default column family"
      (with-system [{db ::kv/rocksdb} (a-config dir)]

        (testing "delete the column family :a"
          (rocksdb/drop-column-family! db :a))))

    (testing "reopen the same DB with the :default column family only"
      (with-system [{db ::kv/rocksdb} (config dir)]

        (is (= [:default] (rocksdb/column-families db))))))

  (testing "with unknown column-family"
    (with-system [{db ::kv/rocksdb} (config (new-temp-dir!))]
      (given (rocksdb/drop-column-family! db :column-family-191453)
        ::anom/category := ::anom/not-found
        ::anom/message := "Column family `column-family-191453` not found."))))
