(ns blaze.db.kv.rocksdb-test
  (:require
    [blaze.anomaly :as ba]
    [blaze.byte-buffer :as bb]
    [blaze.db.kv :as kv]
    [blaze.db.kv-spec]
    [blaze.db.kv.rocksdb :as rocksdb]
    [blaze.db.kv.rocksdb-spec]
    [blaze.db.kv.rocksdb.impl-spec]
    [blaze.test-util :refer [bytes= given-thrown with-system]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [integrant.core :as ig])
  (:import
    [java.nio.file Files]
    [java.nio.file.attribute FileAttribute]))


(set! *warn-on-reflection* true)
(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


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
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :block-cache))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :stats))))

  (testing "invalid dir"
    (given-thrown (ig/init {::kv/rocksdb {:dir ::invalid}})
      :key := ::kv/rocksdb
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :block-cache))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :stats))
      [:explain ::s/problems 2 :pred] := `string?
      [:explain ::s/problems 2 :val] := ::invalid))

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
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :block-cache))
      [:explain ::s/problems 2 :via] := [::rocksdb/stats]
      [:explain ::s/problems 2 :val] := ::invalid)))


(defn- new-temp-dir! []
  (str (Files/createTempDirectory "blaze" (make-array FileAttribute 0))))


(defn- system [dir]
  {::kv/rocksdb
   {:dir dir
    :block-cache (ig/ref ::rocksdb/block-cache)
    :stats (ig/ref ::rocksdb/stats)}
   ::rocksdb/block-cache {}
   ::rocksdb/stats {}})


(deftest valid-test
  (with-system [{db ::kv/rocksdb} (system (new-temp-dir!))]
    (with-open [snapshot (kv/new-snapshot db)
                iter (kv/new-iterator snapshot)]
      (testing "iterator is initially invalid"
        (is (not (kv/valid? iter)))))))


(defmacro with-system-data [[binding-form system] entries & body]
  `(with-system [{db# ::kv/rocksdb :as system#} ~system]
     (kv/put! db# ~entries)
     (let [~binding-form system#] ~@body)))


(deftest seek-to-first-test
  (with-system-data [{db ::kv/rocksdb} (system (new-temp-dir!))]
    [[(ba 0x01) (ba 0x10)]
     [(ba 0x02) (ba 0x20)]]

    (with-open [snapshot (kv/new-snapshot db)
                iter (kv/new-iterator snapshot)]

      (kv/seek-to-first! iter)
      (is (kv/valid? iter))
      (is (bytes= (ba 0x01) (kv/key iter)))
      (is (bytes= (ba 0x10) (kv/value iter))))))


(deftest seek-to-last-test
  (with-system-data [{db ::kv/rocksdb} (system (new-temp-dir!))]
    [[(ba 0x01) (ba 0x10)]
     [(ba 0x02) (ba 0x20)]]

    (with-open [snapshot (kv/new-snapshot db)
                iter (kv/new-iterator snapshot)]

      (kv/seek-to-last! iter)
      (is (kv/valid? iter))
      (is (bytes= (ba 0x02) (kv/key iter)))
      (is (bytes= (ba 0x20) (kv/value iter))))))


(defn- reverse-comparator-system [dir]
  {::kv/rocksdb
   {:dir dir
    :block-cache (ig/ref ::rocksdb/block-cache)
    :stats (ig/ref ::rocksdb/stats)
    :column-families {:a {:reverse-comparator? true}}}
   ::rocksdb/block-cache {}
   ::rocksdb/stats {}})


(deftest seek-test
  (with-system-data [{db ::kv/rocksdb} (system (new-temp-dir!))]
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
    (with-system-data [{db ::kv/rocksdb} (reverse-comparator-system (new-temp-dir!))]
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
  (with-system-data [{db ::kv/rocksdb} (system (new-temp-dir!))]
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
    (with-system-data [{db ::kv/rocksdb} (reverse-comparator-system (new-temp-dir!))]
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
  (with-system-data [{db ::kv/rocksdb} (system (new-temp-dir!))]
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
  (with-system-data [{db ::kv/rocksdb} (system (new-temp-dir!))]
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
  (with-system-data [{db ::kv/rocksdb} (system (new-temp-dir!))]
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
  (with-system-data [{db ::kv/rocksdb} (system (new-temp-dir!))]
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
  (with-system-data [{db ::kv/rocksdb} (system (new-temp-dir!))]
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


(defn- a-b-system [dir]
  {::kv/rocksdb
   {:dir dir
    :block-cache (ig/ref ::rocksdb/block-cache)
    :stats (ig/ref ::rocksdb/stats)
    :column-families {:a nil :b nil}}
   ::rocksdb/block-cache {}
   ::rocksdb/stats {}})


(deftest different-column-families-test
  (with-system-data [{db ::kv/rocksdb} (a-b-system (new-temp-dir!))]
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


(defn- a-system [dir]
  {::kv/rocksdb
   {:dir dir
    :block-cache (ig/ref ::rocksdb/block-cache)
    :stats (ig/ref ::rocksdb/stats)
    :column-families {:a nil}}
   ::rocksdb/block-cache {}
   ::rocksdb/stats {}})


(deftest snapshot-get-test
  (with-system-data [{db ::kv/rocksdb} (a-system (new-temp-dir!))]
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
  (with-system-data [{db ::kv/rocksdb} (a-system (new-temp-dir!))]
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
  (with-system-data [{db ::kv/rocksdb} (system (new-temp-dir!))]
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
  (with-system [{db ::kv/rocksdb} (system (new-temp-dir!))]

    (testing "key value"
      (kv/put! db (ba 0x00) (ba 0x01))
      (is (bytes= (ba 0x01) (kv/get db (ba 0x00)))))

    (testing "entries"
      (kv/put! db [[:default (ba 0x00) (ba 0x01)]])
      (is (bytes= (ba 0x01) (kv/get db (ba 0x00)))))

    (testing "errors on unknown column-family"
      (is (ba/not-found? (ba/try-anomaly (kv/put! db [[:a (ba 0x00) (ba 0x01)]])))))))


(deftest delete-test
  (with-system-data [{db ::kv/rocksdb} (system (new-temp-dir!))]
    [[(ba 0x00) (ba 0x10)]]

    (kv/delete! db [(ba 0x00)])

    (is (nil? (kv/get db (ba 0x00))))))


(defn- merge-system [dir]
  {::kv/rocksdb
   {:dir dir
    :block-cache (ig/ref ::rocksdb/block-cache)
    :stats (ig/ref ::rocksdb/stats)
    :column-families {:default {:merge-operator :stringappend}}}
   ::rocksdb/block-cache {}
   ::rocksdb/stats {}})


(defn- merge-a-system [dir]
  {::kv/rocksdb
   {:dir dir
    :block-cache (ig/ref ::rocksdb/block-cache)
    :stats (ig/ref ::rocksdb/stats)
    :column-families {:a {:merge-operator :stringappend}}}
   ::rocksdb/block-cache {}
   ::rocksdb/stats {}})


(deftest write-test
  (testing "default column-family"
    (with-system-data [{db ::kv/rocksdb} (merge-system (new-temp-dir!))]
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
    (with-system-data [{db ::kv/rocksdb} (merge-a-system (new-temp-dir!))]
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


(deftest get-property-test
  (with-system [{db ::kv/rocksdb} (system (new-temp-dir!))]

    (is (= "0" (rocksdb/get-property db "rocksdb.num-files-at-level0")))
    (is (= "0" (rocksdb/get-property db "rocksdb.num-files-at-level1")))

    (is (string? (rocksdb/get-property db "rocksdb.stats")))

    (is (string? (rocksdb/get-property db "rocksdb.sstables"))))

  (testing "with column-family"
    (with-system [{db ::kv/rocksdb} (a-system (new-temp-dir!))]

      (is (= "0" (rocksdb/get-property db :a "rocksdb.num-files-at-level0")))
      (is (= "0" (rocksdb/get-property db :a "rocksdb.num-files-at-level1")))

      (is (string? (rocksdb/get-property db :a "rocksdb.stats")))

      (is (string? (rocksdb/get-property db :a "rocksdb.sstables"))))))


(deftest compact-range-test
  (with-system-data [{db ::kv/rocksdb} (system (new-temp-dir!))]
    [[(ba 0x00) (ba 0x10)]]

    (rocksdb/compact-range! db)

    (is (bytes= (ba 0x10) (kv/get db (ba 0x00)))))

  (testing "with column-family"
    (with-system-data [{db ::kv/rocksdb} (a-system (new-temp-dir!))]
      [[:a (ba 0x00) (ba 0x10)]]

      (rocksdb/compact-range! db :a true 1)

      (is (bytes= (ba 0x10) (kv/get db :a (ba 0x00)))))))
