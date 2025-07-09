(ns blaze.db.kv.mem-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.byte-buffer :as bb]
   [blaze.db.kv :as kv]
   [blaze.db.kv-spec]
   [blaze.db.kv.mem]
   [blaze.db.kv.mem-spec]
   [blaze.db.kv.protocols :as p]
   [blaze.module.test-util :refer [given-failed-future given-failed-system with-system]]
   [blaze.test-util :as tu :refer [ba bb bytes=]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def config
  {::kv/mem {:column-families {}}})

(def reverse-comparator-config
  {::kv/mem {:column-families {:a {:reverse-comparator? true}}}})

(def a-b-config
  {::kv/mem {:column-families {:a nil :b nil}}})

(defmacro with-system-data
  "Runs `body` inside a system that is initialized from `config`, bound to
  `binding-form` and finally halted.

  Additionally the database is initialized with `entries`."
  [[binding-form config] entries & body]
  `(with-system [~binding-form (assoc-in ~config [::kv/mem :init-data] ~entries)]
     ~@body))

(deftest init-test
  (testing "nil config"
    (given-failed-system {::kv/mem nil}
      :key := ::kv/mem
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {::kv/mem {}}
      :key := ::kv/mem
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :column-families))))

  (testing "invalid column-families"
    (given-failed-system {::kv/mem {:column-families ::invalid}}
      :key := ::kv/mem
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::kv/column-families]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(defn- iterator-invalid-anom? [anom]
  (and (ba/fault? anom) (= "The iterator is invalid." (::anom/message anom))))

(defn- iterator-closed-anom? [anom]
  (and (ba/fault? anom) (= "The iterator is closed." (::anom/message anom))))

(defn- close! [x]
  (.close ^AutoCloseable x))

(deftest valid-test
  (with-system [{kv-store ::kv/mem} config]
    (doseq [new-snapshot [kv/new-snapshot p/-new-snapshot]]
      (with-open [^AutoCloseable snapshot (new-snapshot kv-store)
                  iter (kv/new-iterator snapshot :default)]
        (testing "iterator is initially invalid"
          (is (not (kv/valid? iter))))

        (testing "errors on closed iterator"
          (close! iter)
          (is (iterator-closed-anom? (ba/try-anomaly (kv/valid? iter)))))))))

(deftest seek-to-first-test
  (with-system-data [{kv-store ::kv/mem} config]
    [[:default (ba 0x01) (ba 0x10)]
     [:default (ba 0x02) (ba 0x20)]]

    (doseq [new-snapshot [kv/new-snapshot p/-new-snapshot]]
      (with-open [^AutoCloseable snapshot (new-snapshot kv-store)
                  iter (kv/new-iterator snapshot :default)]

        (kv/seek-to-first! iter)
        (is (kv/valid? iter))
        (is (bytes= (ba 0x01) (kv/key iter)))
        (is (bytes= (ba 0x10) (kv/value iter)))

        (testing "errors on closed iterator"
          (close! iter)
          (is (iterator-closed-anom? (ba/try-anomaly (kv/seek-to-first! iter)))))))))

(deftest seek-to-last-test
  (with-system-data [{kv-store ::kv/mem} config]
    [[:default (ba 0x01) (ba 0x10)]
     [:default (ba 0x02) (ba 0x20)]]

    (doseq [new-snapshot [kv/new-snapshot p/-new-snapshot]]
      (with-open [^AutoCloseable snapshot (new-snapshot kv-store)
                  iter (kv/new-iterator snapshot :default)]

        (kv/seek-to-last! iter)
        (is (kv/valid? iter))
        (is (bytes= (ba 0x02) (kv/key iter)))
        (is (bytes= (ba 0x20) (kv/value iter)))

        (testing "errors on closed iterator"
          (close! iter)
          (is (iterator-closed-anom? (ba/try-anomaly (kv/seek-to-last! iter)))))))))

(deftest seek-test
  (with-system-data [{kv-store ::kv/mem} config]
    [[:default (ba 0x01) (ba 0x10)]
     [:default (ba 0x03) (ba 0x30)]]

    (doseq [new-snapshot [kv/new-snapshot p/-new-snapshot]]
      (with-open [^AutoCloseable snapshot (new-snapshot kv-store)
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
          (is (not (kv/valid? iter))))

        (testing "errors on closed iterator"
          (close! iter)
          (is (iterator-closed-anom? (ba/try-anomaly (kv/seek! iter (ba 0x00)))))))))

  (testing "reverse comparator"
    (with-system-data [{kv-store ::kv/mem} reverse-comparator-config]
      [[:a (ba 0x01) (ba 0x10)]
       [:a (ba 0x03) (ba 0x30)]]

      (with-open [snapshot (kv/new-snapshot kv-store)
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
          (is (not (kv/valid? iter))))

        (testing "errors on closed iterator"
          (close! iter)
          (is (iterator-closed-anom? (ba/try-anomaly (kv/seek! iter (ba 0x04))))))))))

(deftest seek-buffer-test
  (with-system-data [{kv-store ::kv/mem} config]
    [[:default (ba 0x01) (ba 0x10)]
     [:default (ba 0x03) (ba 0x30)]]

    (doseq [new-snapshot [kv/new-snapshot p/-new-snapshot]]
      (with-open [^AutoCloseable snapshot (new-snapshot kv-store)
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
          (is (not (kv/valid? iter))))

        (testing "errors on closed iterator"
          (close! iter)
          (is (iterator-closed-anom? (ba/try-anomaly (kv/seek-buffer! iter (bb 0x00)))))))))

  (testing "reverse comparator"
    (with-system-data [{kv-store ::kv/mem} reverse-comparator-config]
      [[:a (ba 0x01) (ba 0x10)]
       [:a (ba 0x03) (ba 0x30)]]

      (with-open [snapshot (kv/new-snapshot kv-store)
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
          (is (not (kv/valid? iter))))

        (testing "errors on closed iterator"
          (close! iter)
          (is (iterator-closed-anom? (ba/try-anomaly (kv/seek-buffer! iter (bb 0x04))))))))))

(deftest seek-for-prev-test
  (with-system-data [{kv-store ::kv/mem} config]
    [[:default (ba 0x01) (ba 0x10)]
     [:default (ba 0x03) (ba 0x30)]]

    (doseq [new-snapshot [kv/new-snapshot p/-new-snapshot]]
      (with-open [^AutoCloseable snapshot (new-snapshot kv-store)
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
          (is (not (kv/valid? iter))))

        (testing "errors on closed iterator"
          (close! iter)
          (is (iterator-closed-anom? (ba/try-anomaly (kv/seek-for-prev! iter (ba 0x00))))))))))

(deftest seek-for-prev-buffer-test
  (with-system-data [{kv-store ::kv/mem} config]
    [[:default (ba 0x01) (ba 0x10)]
     [:default (ba 0x03) (ba 0x30)]]

    (doseq [new-snapshot [kv/new-snapshot p/-new-snapshot]]
      (with-open [^AutoCloseable snapshot (new-snapshot kv-store)
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
          (is (not (kv/valid? iter))))

        (testing "errors on closed iterator"
          (close! iter)
          (is (iterator-closed-anom? (ba/try-anomaly (kv/seek-for-prev-buffer! iter (bb 0x00))))))))))

(deftest next-test
  (with-system-data [{kv-store ::kv/mem} config]
    [[:default (ba 0x01) (ba 0x10)]
     [:default (ba 0x03) (ba 0x30)]]

    (doseq [new-snapshot [kv/new-snapshot p/-new-snapshot]]
      (with-open [^AutoCloseable snapshot (new-snapshot kv-store)
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
          (is (not (kv/valid? iter))))

        (testing "iterator is invalid"
          (is (iterator-invalid-anom? (ba/try-anomaly (kv/next! iter)))))

        (testing "errors on closed iterator"
          (close! iter)
          (is (iterator-closed-anom? (ba/try-anomaly (kv/next! iter)))))))))

(deftest prev-test
  (with-system-data [{kv-store ::kv/mem} config]
    [[:default (ba 0x01) (ba 0x10)]
     [:default (ba 0x03) (ba 0x30)]]

    (doseq [new-snapshot [kv/new-snapshot p/-new-snapshot]]
      (with-open [^AutoCloseable snapshot (new-snapshot kv-store)
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
          (is (not (kv/valid? iter))))

        (testing "iterator is invalid"
          (is (iterator-invalid-anom? (ba/try-anomaly (kv/prev! iter)))))

        (testing "errors on closed iterator"
          (close! iter)
          (is (iterator-closed-anom? (ba/try-anomaly (kv/prev! iter)))))))))

(deftest key-test
  (with-system-data [{kv-store ::kv/mem} config]
    [[:default (ba 0x01 0x02) (ba 0x00)]]

    (doseq [new-snapshot [kv/new-snapshot p/-new-snapshot]]
      (with-open [^AutoCloseable snapshot (new-snapshot kv-store)
                  iter (kv/new-iterator snapshot :default)]

        (testing "errors on invalid iterator"
          (is (iterator-invalid-anom? (ba/try-anomaly (kv/key iter))))
          (is (iterator-invalid-anom? (ba/try-anomaly (kv/key! iter (bb/allocate 0))))))

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
            (is (= 0x02 (bb/get-byte! buf)))))

        (testing "errors on closed iterator"
          (close! iter)
          (is (iterator-closed-anom? (ba/try-anomaly (kv/key iter))))
          (is (iterator-closed-anom? (ba/try-anomaly (kv/key! iter (bb/allocate 0))))))))))

(deftest value-test
  (with-system-data [{kv-store ::kv/mem} config]
    [[:default (ba 0x00) (ba 0x01 0x02)]]

    (doseq [new-snapshot [kv/new-snapshot p/-new-snapshot]]
      (with-open [^AutoCloseable snapshot (new-snapshot kv-store)
                  iter (kv/new-iterator snapshot :default)]

        (testing "errors on invalid iterator"
          (is (iterator-invalid-anom? (ba/try-anomaly (kv/value iter))))
          (is (iterator-invalid-anom? (ba/try-anomaly (kv/value! iter (bb/allocate 0))))))

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
            (is (= 0x02 (bb/get-byte! buf)))))

        (testing "errors on closed iterator"
          (close! iter)
          (is (iterator-closed-anom? (ba/try-anomaly (kv/value iter))))
          (is (iterator-closed-anom? (ba/try-anomaly (kv/value! iter (bb/allocate 0))))))))))

(deftest different-column-families-test
  (with-system-data [{kv-store ::kv/mem} a-b-config]
    [[:a (ba 0x00) (ba 0x01)]
     [:b (ba 0x00) (ba 0x02)]]

    (with-open [snapshot (kv/new-snapshot kv-store)
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

(deftest snapshot-get-test
  (with-system-data [{kv-store ::kv/mem} config]
    [[:default (ba 0x00) (ba 0x01)]]

    (with-open [snapshot (kv/new-snapshot kv-store)]

      (testing "returns found value"
        (is (bytes= (ba 0x01) (kv/snapshot-get snapshot :default (ba 0x00)))))

      (testing "returns nil on not found value"
        (is (nil? (kv/snapshot-get snapshot :default (ba 0x01))))))))

(deftest get-test
  (with-system-data [{kv-store ::kv/mem} config]
    [[:default (ba 0x00) (ba 0x01)]]

    (testing "returns found value"
      (is (bytes= (ba 0x01) (kv/get kv-store :default (ba 0x00)))))

    (testing "returns nil on not found value"
      (is (nil? (kv/get kv-store :default (ba 0x01)))))))

(deftest put-test
  (with-system [{kv-store ::kv/mem} config]

    (testing "get after put"
      (kv/put! kv-store [[:default (ba 0x00) (ba 0x01)]])
      (is (bytes= (ba 0x01) (kv/get kv-store :default (ba 0x00)))))

    (testing "errors on unknown column-family"
      (is (ba/not-found? (ba/try-anomaly (kv/put! kv-store [[:a (ba 0x00) (ba 0x01)]])))))))

(deftest delete-test
  (with-system-data [{kv-store ::kv/mem} config]
    [[:default (ba 0x00) (ba 0x10)]]

    (kv/delete! kv-store [[:default (ba 0x00)]])

    (is (nil? (kv/get kv-store :default (ba 0x00))))))

(deftest write-test
  (with-system-data [{kv-store ::kv/mem} config]
    [[:default (ba 0x00) (ba 0x10)]]

    (testing "put"
      (kv/write! kv-store [[:put :default (ba 0x01) (ba 0x11)]])
      (is (bytes= (ba 0x11) (kv/get kv-store :default (ba 0x01)))))

    (testing "merge is not supported"
      (is (ba/unsupported? (ba/try-anomaly (kv/write! kv-store [[:merge :default (ba 0x00) (ba 0x00)]])))))

    (testing "delete"
      (kv/write! kv-store [[:delete :default (ba 0x00)]])
      (is (nil? (kv/get kv-store :default (ba 0x00)))))))

(deftest estimate-num-keys-test
  (with-system [{kv-store ::kv/mem} config]
    (is (zero? (kv/estimate-num-keys kv-store :default)))

    (given (kv/estimate-num-keys kv-store :foo)
      ::anom/category := ::anom/not-found
      ::anom/message := "Column family `foo` not found."))

  (with-system-data [{kv-store ::kv/mem} config]
    [[:default (ba 0x00) (ba 0x10)]]

    (is (= 1 (kv/estimate-num-keys kv-store :default)))))

(deftest estimate-storage-scan-test
  (with-system [{kv-store ::kv/mem} config]
    (given (kv/estimate-scan-size kv-store :foo [#blaze/byte-string"00" #blaze/byte-string"FF"])
      ::anom/category := ::anom/unsupported
      ::anom/message := "In-Memory KV Store doesn't support estimating the scan size.")))

(deftest compact-test
  (with-system [{kv-store ::kv/mem} config]
    (is (nil? @(kv/compact! kv-store :default)))

    (given-failed-future (kv/compact! kv-store :foo)
      ::anom/category := ::anom/not-found
      ::anom/message := "Column family `foo` not found.")))
