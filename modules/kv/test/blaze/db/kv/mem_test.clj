(ns blaze.db.kv.mem-test
  (:require
    [blaze.db.bytes :as bytes]
    [blaze.db.kv :as kv]
    [blaze.db.kv-spec]
    [blaze.db.kv.mem]
    [blaze.db.kv.mem-spec]
    [blaze.log]
    [blaze.test-util :refer [given-thrown with-system]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import
    [java.nio ByteBuffer]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def system
  {::kv/mem {:column-families {}}})


(def reverse-comparator-system
  {::kv/mem {:column-families {:a {:reverse-comparator? true}}}})


(def a-system
  {::kv/mem {:column-families {:a nil}}})


(def a-b-system
  {::kv/mem {:column-families {:a nil :b nil}}})


(defmacro with-system-data [[binding-form system] entries & body]
  `(with-system [system# ~system]
     (kv/put! (::kv/mem system#) ~entries)
     (let [~binding-form system#] ~@body)))


(defn- ba [& bytes]
  (byte-array bytes))


(defmacro catch-ex-data [& body]
  `(try
     ~@body
     (catch Exception e#
       (ex-data e#))))


(defn- fault? [anomaly]
  (= ::anom/fault (::anom/category anomaly)))


(defn- unsupported? [anomaly]
  (= ::anom/unsupported (::anom/category anomaly)))


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {::kv/mem nil})
      :key := ::kv/mem
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {::kv/mem {}})
      :key := ::kv/mem
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :column-families)))))


(deftest valid-test
  (with-system [{kv-store ::kv/mem} system]
    (let [iter (kv/new-iterator (kv/new-snapshot kv-store))]
      (testing "iterator is initially invalid"
        (is (not (kv/valid? iter))))

      (testing "errors on closed iterator"
        (.close iter)
        (is (fault? (catch-ex-data (kv/valid? iter))))))))


(deftest seek-to-first-test
  (with-system [{kv-store ::kv/mem} system]
    (let [iter (kv/new-iterator (kv/new-snapshot kv-store))]
      (testing "errors on closed iterator"
        (.close iter)
        (is (fault? (catch-ex-data (kv/seek-to-first! iter))))))))


(deftest seek-to-last-test
  (with-system [{kv-store ::kv/mem} system]
    (let [iter (kv/new-iterator (kv/new-snapshot kv-store))]
      (testing "errors on closed iterator"
        (.close iter)
        (is (fault? (catch-ex-data (kv/seek-to-last! iter))))))))


(deftest seek-test
  (with-system-data [{kv-store ::kv/mem} system]
    [[(ba 0x01) (ba 0x10)]
     [(ba 0x03) (ba 0x30)]]

    (let [iter (kv/new-iterator (kv/new-snapshot kv-store))]
      (testing "before first entry"
        (kv/seek! iter (ba 0x00))
        (is (kv/valid? iter))
        (is (bytes/= (ba 0x01) (kv/key iter)))
        (is (bytes/= (ba 0x10) (kv/value iter))))

      (testing "at first entry"
        (kv/seek! iter (ba 0x01))
        (is (kv/valid? iter))
        (is (bytes/= (ba 0x01) (kv/key iter)))
        (is (bytes/= (ba 0x10) (kv/value iter))))

      (testing "before second entry"
        (kv/seek! iter (ba 0x02))
        (is (kv/valid? iter))
        (is (bytes/= (ba 0x03) (kv/key iter)))
        (is (bytes/= (ba 0x30) (kv/value iter))))

      (testing "at second entry"
        (kv/seek! iter (ba 0x03))
        (is (kv/valid? iter))
        (is (bytes/= (ba 0x03) (kv/key iter)))
        (is (bytes/= (ba 0x30) (kv/value iter))))

      (testing "overshoot"
        (kv/seek! iter (ba 0x04))
        (is (not (kv/valid? iter))))

      (testing "errors on closed iterator"
        (.close iter)
        (is (fault? (catch-ex-data (kv/seek! iter (ba 0x00))))))))

  (testing "reverse comparator"
    (with-system-data [{kv-store ::kv/mem} reverse-comparator-system]
      [[:a (ba 0x01) (ba 0x10)]
       [:a (ba 0x03) (ba 0x30)]]

      (let [iter (kv/new-iterator (kv/new-snapshot kv-store) :a)]
        (testing "before first entry"
          (kv/seek! iter (ba 0x04))
          (is (kv/valid? iter))
          (is (bytes/= (ba 0x03) (kv/key iter)))
          (is (bytes/= (ba 0x30) (kv/value iter))))

        (testing "at first entry"
          (kv/seek! iter (ba 0x03))
          (is (kv/valid? iter))
          (is (bytes/= (ba 0x03) (kv/key iter)))
          (is (bytes/= (ba 0x30) (kv/value iter))))

        (testing "before second entry"
          (kv/seek! iter (ba 0x02))
          (is (kv/valid? iter))
          (is (bytes/= (ba 0x01) (kv/key iter)))
          (is (bytes/= (ba 0x10) (kv/value iter))))

        (testing "at second entry"
          (kv/seek! iter (ba 0x01))
          (is (kv/valid? iter))
          (is (bytes/= (ba 0x01) (kv/key iter)))
          (is (bytes/= (ba 0x10) (kv/value iter))))

        (testing "overshoot"
          (kv/seek! iter (ba 0x00))
          (is (not (kv/valid? iter))))

        (testing "errors on closed iterator"
          (.close iter)
          (is (fault? (catch-ex-data (kv/seek! iter (ba 0x04))))))))))


(deftest seek-for-prev-test
  (with-system-data [{kv-store ::kv/mem} system]
    [[(ba 0x01) (ba 0x10)]
     [(ba 0x03) (ba 0x30)]]

    (let [iter (kv/new-iterator (kv/new-snapshot kv-store))]
      (testing "past second entry"
        (kv/seek-for-prev! iter (ba 0x04))
        (is (kv/valid? iter))
        (is (bytes/= (ba 0x03) (kv/key iter)))
        (is (bytes/= (ba 0x30) (kv/value iter))))

      (testing "at second entry"
        (kv/seek-for-prev! iter (ba 0x03))
        (is (kv/valid? iter))
        (is (bytes/= (ba 0x03) (kv/key iter)))
        (is (bytes/= (ba 0x30) (kv/value iter))))

      (testing "past first entry"
        (kv/seek-for-prev! iter (ba 0x02))
        (is (kv/valid? iter))
        (is (bytes/= (ba 0x01) (kv/key iter)))
        (is (bytes/= (ba 0x10) (kv/value iter))))

      (testing "at first entry"
        (kv/seek-for-prev! iter (ba 0x01))
        (is (kv/valid? iter))
        (is (bytes/= (ba 0x01) (kv/key iter)))
        (is (bytes/= (ba 0x10) (kv/value iter))))

      (testing "overshoot"
        (kv/seek-for-prev! iter (ba 0x00))
        (is (not (kv/valid? iter))))

      (testing "errors on closed iterator"
        (.close iter)
        (is (fault? (catch-ex-data (kv/seek-for-prev! iter (ba 0x00)))))))))


(deftest next-test
  (with-system-data [{kv-store ::kv/mem} system]
    [[(ba 0x01) (ba 0x10)]
     [(ba 0x03) (ba 0x30)]]

    (let [iter (kv/new-iterator (kv/new-snapshot kv-store))]
      (testing "first entry"
        (kv/seek-to-first! iter)
        (is (kv/valid? iter))
        (is (bytes/= (ba 0x01) (kv/key iter)))
        (is (bytes/= (ba 0x10) (kv/value iter))))

      (testing "second entry"
        (kv/next! iter)
        (is (kv/valid? iter))
        (is (bytes/= (ba 0x03) (kv/key iter)))
        (is (bytes/= (ba 0x30) (kv/value iter))))

      (testing "end"
        (kv/next! iter)
        (is (not (kv/valid? iter))))

      (testing "iterator is invalid"
        (try
          (kv/next! iter)
          (catch Exception e
            (is (fault? (ex-data e)))))))))


(deftest prev-test
  (with-system-data [{kv-store ::kv/mem} system]
    [[(ba 0x01) (ba 0x10)]
     [(ba 0x03) (ba 0x30)]]

    (let [iter (kv/new-iterator (kv/new-snapshot kv-store))]
      (testing "first entry"
        (kv/seek-to-last! iter)
        (is (kv/valid? iter))
        (is (bytes/= (ba 0x03) (kv/key iter)))
        (is (bytes/= (ba 0x30) (kv/value iter))))

      (testing "second entry"
        (kv/prev! iter)
        (is (kv/valid? iter))
        (is (bytes/= (ba 0x01) (kv/key iter)))
        (is (bytes/= (ba 0x10) (kv/value iter))))

      (testing "end"
        (kv/prev! iter)
        (is (not (kv/valid? iter))))

      (testing "iterator is invalid"
        (try
          (kv/prev! iter)
          (catch Exception e
            (is (fault? (ex-data e)))))))))


(deftest key-test
  (with-system-data [{kv-store ::kv/mem} system]
    [[(ba 0x01 0x02) (ba 0x00)]]

    (let [iter (kv/new-iterator (kv/new-snapshot kv-store))]
      (testing "errors on invalid iterator"
        (is (fault? (catch-ex-data (kv/key iter))))
        (is (fault? (catch-ex-data (kv/key! iter (ByteBuffer/allocateDirect 0))))))

      (testing "errors on nil buffer"
        (kv/seek-to-first! iter)
        (is (thrown-with-msg? Exception #"Call to #'blaze.db.kv/key! did not conform to spec."
                              (kv/key! iter nil))))

      (testing "errors on heap buffer"
        (kv/seek-to-first! iter)
        (is (thrown-with-msg? Exception #"Call to #'blaze.db.kv/key! did not conform to spec."
                              (kv/key! iter (ByteBuffer/allocate 0)))))

      (testing "puts the first byte into the buffer without overflowing"
        (kv/seek-to-first! iter)
        (let [buf (ByteBuffer/allocateDirect 1)]
          (is (= 2 (kv/key! iter buf)))
          (is (= 0x01 (.get buf)))))

      (testing "sets the limit of a bigger buffer to two"
        (kv/seek-to-first! iter)
        (let [buf (ByteBuffer/allocateDirect 3)]
          (is (= 2 (kv/key! iter buf)))
          (is (= 2 (.limit buf)))))

      (testing "writes the key at position"
        (kv/seek-to-first! iter)
        (let [buf (ByteBuffer/allocateDirect 3)]
          (.position buf 1)
          (is (= 2 (kv/key! iter buf)))
          (is (= 1 (.position buf)))
          (is (= 3 (.limit buf)))
          (is (= 0x00 (.get buf 0)))
          (is (= 0x01 (.get buf)))
          (is (= 0x02 (.get buf))))))))


(deftest value-test
  (with-system-data [{kv-store ::kv/mem} system]
    [[(ba 0x00) (ba 0x01 0x02)]]

    (let [iter (kv/new-iterator (kv/new-snapshot kv-store))]
      (testing "errors on invalid iterator"
        (is (fault? (catch-ex-data (kv/value iter))))
        (is (fault? (catch-ex-data (kv/value! iter (ByteBuffer/allocateDirect 0))))))

      (testing "puts the first byte into the buffer without overflowing"
        (kv/seek-to-first! iter)
        (let [buf (ByteBuffer/allocateDirect 1)]
          (is (= 2 (kv/value! iter buf)))
          (is (= 0x01 (.get buf)))))

      (testing "sets the limit of a bigger buffer to two"
        (kv/seek-to-first! iter)
        (let [buf (ByteBuffer/allocateDirect 3)]
          (is (= 2 (kv/value! iter buf)))
          (is (= 2 (.limit buf)))))

      (testing "writes the value at position"
        (kv/seek-to-first! iter)
        (let [buf (ByteBuffer/allocateDirect 3)]
          (.position buf 1)
          (is (= 2 (kv/value! iter buf)))
          (is (= 1 (.position buf)))
          (is (= 3 (.limit buf)))
          (is (= 0x00 (.get buf 0)))
          (is (= 0x01 (.get buf)))
          (is (= 0x02 (.get buf))))))))


(deftest different-column-families-test
  (with-system-data [{kv-store ::kv/mem} a-b-system]
    [[:a (ba 0x00) (ba 0x01)]
     [:b (ba 0x00) (ba 0x02)]]

    (let [snapshot (kv/new-snapshot kv-store)
          iter-a (kv/new-iterator snapshot :a)
          iter-b (kv/new-iterator snapshot :b)]
      (testing "the value in :a is 0x01"
        (kv/seek-to-first! iter-a)
        (is (bytes/= (ba 0x01) (kv/value iter-a))))

      (testing "the value in :b is 0x02"
        (kv/seek-to-first! iter-b)
        (is (bytes/= (ba 0x02) (kv/value iter-b))))

      (testing "column-family :c doesn't exist"
        (is (fault? (catch-ex-data (kv/new-iterator snapshot :c))))))))


(deftest snapshot-get-test
  (with-system-data [{kv-store ::kv/mem} a-system]
    [[(ba 0x00) (ba 0x01)]
     [:a (ba 0x00) (ba 0x02)]]

    (let [snapshot (kv/new-snapshot kv-store)]
      (testing "returns found value"
        (is (bytes/= (ba 0x01) (kv/snapshot-get snapshot (ba 0x00)))))

      (testing "returns nil on not found value"
        (is (nil? (kv/snapshot-get snapshot (ba 0x01)))))

      (testing "returns found value of column-family :a"
        (is (bytes/= (ba 0x02) (kv/snapshot-get snapshot :a (ba 0x00)))))

      (testing "returns nil on not found value of column-family :a"
        (is (nil? (kv/snapshot-get snapshot :a (ba 0x01))))))))


(deftest get-test
  (with-system-data [{kv-store ::kv/mem} a-system]
    [[(ba 0x00) (ba 0x01)]
     [:a (ba 0x00) (ba 0x02)]]

    (testing "returns found value"
      (is (bytes/= (ba 0x01) (kv/get kv-store (ba 0x00)))))

    (testing "returns nil on not found value"
      (is (nil? (kv/get kv-store (ba 0x01)))))

    (testing "returns found value of column-family :a"
      (is (bytes/= (ba 0x02) (kv/get kv-store :a (ba 0x00)))))

    (testing "returns nil on not found value of column-family :a"
      (is (nil? (kv/get kv-store :a (ba 0x01)))))))


(deftest multi-get-test
  (with-system-data [{kv-store ::kv/mem} system]
    [[(ba 0x00) (ba 0x10)]
     [(ba 0x01) (ba 0x11)]]

    (testing "returns all found entries"
      (let [m (into
                {}
                (map (fn [[k v]] [(vec k) (vec v)]))
                (kv/multi-get kv-store [(ba 0x00) (ba 0x01) (ba 0x02)]))]
        (is (= [0x10] (get m [0x00])))
        (is (= [0x11] (get m [0x01])))))))


(deftest put-test
  (with-system [{kv-store ::kv/mem} system]

    (testing "key value"
      (kv/put! kv-store (ba 0x00) (ba 0x01))
      (is (bytes/= (ba 0x01) (kv/get kv-store (ba 0x00)))))

    (testing "errors on unknown column-family"
      (is (thrown? AssertionError (kv/put! kv-store [[:a (ba 0x00) (ba 0x01)]]))))))


(deftest delete-test
  (with-system-data [{kv-store ::kv/mem} system]
    [[(ba 0x00) (ba 0x10)]]

    (kv/delete! kv-store [(ba 0x00)])

    (is (nil? (kv/get kv-store (ba 0x00))))))


(deftest write-test
  (testing "default column-family"
    (with-system-data [{kv-store ::kv/mem} system]
      [[(ba 0x00) (ba 0x10)]]

      (testing "put"
        (kv/write! kv-store [[:put (ba 0x01) (ba 0x11)]])
        (is (bytes/= (ba 0x11) (kv/get kv-store (ba 0x01)))))

      (testing "merge is not supported"
        (is (unsupported? (catch-ex-data (kv/write! kv-store [[:merge]])))))

      (testing "delete"
        (kv/write! kv-store [[:delete (ba 0x00)]])
        (is (nil? (kv/get kv-store (ba 0x00)))))))

  (testing "custom column-family"
    (with-system-data [{kv-store ::kv/mem} a-system]
      [[:a (ba 0x00) (ba 0x10)]]

      (testing "put"
        (kv/write! kv-store [[:put :a (ba 0x01) (ba 0x11)]])
        (is (bytes/= (ba 0x11) (kv/get kv-store :a (ba 0x01)))))

      (testing "merge is not supported"
        (is (unsupported? (catch-ex-data (kv/write! kv-store [[:merge :a]])))))

      (testing "delete"
        (kv/write! kv-store [[:delete :a (ba 0x00)]])
        (is (nil? (kv/get kv-store :a (ba 0x00))))))))


(deftest init-component-test
  (is (kv/store? (ig/init-key ::kv/mem {}))))
