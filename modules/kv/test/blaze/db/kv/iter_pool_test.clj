(ns blaze.db.kv.iter-pool-test
  (:require
   [blaze.anomaly :as ba :refer [throw-anom]]
   [blaze.async.comp :as ac]
   [blaze.db.kv :as kv]
   [blaze.db.kv-spec]
   [blaze.db.kv.iter-pool :as ip]
   [blaze.db.kv.protocols :as p]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [blaze.db.kv.iter_pool PooledIterator PoolingSnapshot State]
   [java.lang AutoCloseable]
   [java.util.concurrent Executors]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def ^:private iterator-closed-anom
  (ba/fault "The iterator is closed."))

(deftype Iterator [column-family closed]
  p/KvIterator
  (-valid [_]
    (when @closed (throw-anom iterator-closed-anom))
    false)
  AutoCloseable
  (close [_]
    (vreset! closed true)))

(deftype Snapshot [state]
  p/KvSnapshot
  (-new-iterator [_ column-family]
    (-> (swap! state update-in [:iterators column-family] conj
               (->Iterator column-family (volatile! false)))
        :iterators column-family first))

  AutoCloseable
  (close [_]
    (swap! state assoc :closed true)))

(defn- snapshot []
  (->Snapshot (atom {:closed false})))

(defn- curr-state [pooling-snapshot]
  (.-pool ^PoolingSnapshot pooling-snapshot))

(defn- orig-iter [pooled-iterator]
  (.-iter ^PooledIterator pooled-iterator))

(defn- orig-iter-closed? [pooled-iterator]
  @(.-closed ^Iterator (orig-iter pooled-iterator)))

(defn- curr-orig-snapshot-state [pooling-snapshot]
  @(.-state ^Snapshot (.-snapshot ^PoolingSnapshot pooling-snapshot)))

(defn- close [closeable]
  (.close ^AutoCloseable closeable))

(defn- curr-orig-iter-column-family [pooled-iterator]
  (.-column-family ^Iterator (orig-iter pooled-iterator)))

(defn- iterator-closed-anom? [anom]
  (and (ba/fault? anom) (= "The iterator is closed." (::anom/message anom))))

(defn- borrowed [state]
  (.borrowed ^State state))

(defn- returned [state]
  (.returned ^State state))

(deftest new-iterator-test
  (testing "borrowing and closing one iterator"
    (let [snapshot (ip/pooling-snapshot (snapshot))
          pooled-iter (p/-new-iterator snapshot :a)]

      (testing "snapshot state after borrowing an iterator"
        (given (curr-state snapshot)
          count := 1
          [:a borrowed] := [(orig-iter pooled-iter)]
          [:a returned] := []))

      (testing "original iterator column family"
        (is (= :a (curr-orig-iter-column-family pooled-iter))))

      (testing "iterator can be used"
        (is (false? (kv/valid? pooled-iter))))

      (close pooled-iter)

      (testing "errors on closed iterator"
        (is (iterator-closed-anom? (ba/try-anomaly (kv/valid? pooled-iter)))))

      (testing "snapshot state after closing the pooled iterator"
        (given (curr-state snapshot)
          count := 1
          [:a borrowed] := []
          [:a returned] := [(orig-iter pooled-iter)]))

      (testing "original iterator isn't closed after closing the pooled iterator"
        (is (false? (orig-iter-closed? pooled-iter))))

      (close snapshot)

      (testing "original iterator is closed after closing the snapshot"
        (is (true? (orig-iter-closed? pooled-iter))))

      (testing "original snapshot is closed after closing the snapshot"
        (given (curr-orig-snapshot-state snapshot)
          :closed := true))))

  (testing "borrowing and closing two iterators"
    (let [snapshot (ip/pooling-snapshot (snapshot))
          pooled-iter-1 (p/-new-iterator snapshot :a)
          pooled-iter-2 (p/-new-iterator snapshot :a)]

      (testing "snapshot state after borrowing two iterators"
        (given (curr-state snapshot)
          count := 1
          [:a borrowed] := [(orig-iter pooled-iter-1) (orig-iter pooled-iter-2)]
          [:a returned] := []))

      (testing "original iterators column family"
        (is (= :a (curr-orig-iter-column-family pooled-iter-1)))
        (is (= :a (curr-orig-iter-column-family pooled-iter-2))))

      (testing "iterators can be used"
        (is (false? (kv/valid? pooled-iter-1)))
        (is (false? (kv/valid? pooled-iter-2))))

      (close pooled-iter-1)
      (close pooled-iter-2)

      (testing "errors on closed iterator"
        (is (iterator-closed-anom? (ba/try-anomaly (kv/valid? pooled-iter-1))))
        (is (iterator-closed-anom? (ba/try-anomaly (kv/valid? pooled-iter-2)))))

      (testing "snapshot state after closing the pooled iterators"
        (given (curr-state snapshot)
          count := 1
          [:a borrowed] := []
          [:a returned] := [(orig-iter pooled-iter-1) (orig-iter pooled-iter-2)]))

      (testing "original iterators aren't closed after closing the pooled iterator"
        (is (false? (orig-iter-closed? pooled-iter-1)))
        (is (false? (orig-iter-closed? pooled-iter-2))))

      (close snapshot)

      (testing "original iterators are closed after closing the snapshot"
        (is (true? (orig-iter-closed? pooled-iter-1)))
        (is (true? (orig-iter-closed? pooled-iter-2))))

      (testing "original snapshot is closed after closing the snapshot"
        (given (curr-orig-snapshot-state snapshot)
          :closed := true))))

  (testing "borrowing and not closing one iterator"
    (let [snapshot (ip/pooling-snapshot (snapshot))
          pooled-iter (p/-new-iterator snapshot :a)]

      (close snapshot)

      (testing "errors on closed iterator"
        (is (iterator-closed-anom? (ba/try-anomaly (kv/valid? pooled-iter)))))

      (testing "original iterator is closed after closing the snapshot"
        (is (true? (orig-iter-closed? pooled-iter))))

      (testing "original snapshot is closed after closing the snapshot"
        (given (curr-orig-snapshot-state snapshot)
          :closed := true))))

  (testing "borrowing and not closing all iterators"
    (let [snapshot (ip/pooling-snapshot (snapshot))
          pooled-iter-1 (p/-new-iterator snapshot :a)
          pooled-iter-2 (p/-new-iterator snapshot :a)
          pooled-iter-3 (p/-new-iterator snapshot :b)
          pooled-iter-4 (p/-new-iterator snapshot :b)]

      (close pooled-iter-4)

      (testing "snapshot state after borrowing both iterators"
        (given (curr-state snapshot)
          count := 2
          [:a borrowed] := [(orig-iter pooled-iter-1) (orig-iter pooled-iter-2)]
          [:a returned] := []
          [:b borrowed] := [(orig-iter pooled-iter-3)]
          [:b returned] := [(orig-iter pooled-iter-4)]))

      (testing "original iterators column families"
        (is (= :a (curr-orig-iter-column-family pooled-iter-1)))
        (is (= :a (curr-orig-iter-column-family pooled-iter-2)))
        (is (= :b (curr-orig-iter-column-family pooled-iter-3)))
        (is (= :b (curr-orig-iter-column-family pooled-iter-4))))

      (close snapshot)

      (testing "errors on closed iterators"
        (are [iter] (iterator-closed-anom? (ba/try-anomaly (kv/valid? iter)))
          pooled-iter-1 pooled-iter-2 pooled-iter-3 pooled-iter-4))

      (testing "original iterators are closed after closing the snapshot"
        (are [iter] (true? (orig-iter-closed? iter))
          pooled-iter-1 pooled-iter-2 pooled-iter-3 pooled-iter-4))

      (testing "original snapshot is closed after closing the snapshot"
        (given (curr-orig-snapshot-state snapshot)
          :closed := true))))

  (testing "re-borrowing an iterator"
    (let [snapshot (ip/pooling-snapshot (snapshot))]
      (close (p/-new-iterator snapshot :a))

      (let [pooled-iter (p/-new-iterator snapshot :a)]

        (testing "snapshot state after borrowing an iterator"
          (given (curr-state snapshot)
            count := 1
            [:a borrowed] := [(orig-iter pooled-iter)]
            [:a returned] := []))

        (testing "original iterator column family"
          (is (= :a (curr-orig-iter-column-family pooled-iter))))

        (testing "iterator can be used"
          (is (false? (kv/valid? pooled-iter)))))))

  (testing "borrowing and closing an iterator from column family a and borrowing one from column family b"
    (let [snapshot (ip/pooling-snapshot (snapshot))
          pooled-iter-a (p/-new-iterator snapshot :a)]

      (close pooled-iter-a)

      (let [pooled-iter-b (p/-new-iterator snapshot :b)]

        (testing "snapshot state after borrowing an iterator"
          (given (curr-state snapshot)
            count := 2
            [:a borrowed] := []
            [:a returned] := [(orig-iter pooled-iter-a)]
            [:b borrowed] := [(orig-iter pooled-iter-b)]
            [:b returned] := []))

        (testing "original iterator column family"
          (is (= :b (curr-orig-iter-column-family pooled-iter-b))))

        (testing "iterator can be used"
          (is (false? (kv/valid? pooled-iter-b)))))))

  (testing "borrowing and returning many iterators from 100 threads in parallel"
    (with-open [snapshot (ip/pooling-snapshot (snapshot))]
      (let [num-threads 100
            executor (Executors/newFixedThreadPool num-threads)
            futures (mapv
                     (fn [column-family]
                       (ac/supply-async
                        #(let [iter (p/-new-iterator snapshot column-family)]
                           (Thread/sleep (long (rand-int 10)))
                           (close iter))
                        executor))
                     (flatten (repeat 10000 [:a :b :c])))]

        ;; wait on all futures
        @(ac/all-of futures)

        (testing "snapshot state"
          (given (curr-state snapshot)
            count := 3
            [:a borrowed] := []
            [:a returned count] :? #(<= % 100)
            [:b borrowed] := []
            [:b returned count] :? #(<= % 100)
            [:c borrowed] := []
            [:c returned count] :? #(<= % 100)))))))

(comment
  (require '[criterium.core :refer [bench quick-bench]])
  (st/unstrument)

  ;; 31 ns
  (with-open [snapshot (ip/pooling-snapshot (snapshot))]
    (quick-bench (close (p/-new-iterator snapshot :a))))

  ;; 63 ns
  (with-open [snapshot (ip/pooling-snapshot (snapshot))]
    (quick-bench
     (let [i1 (p/-new-iterator snapshot :a)
           i2 (p/-new-iterator snapshot :a)]
       (close i1)
       (close i2))))

  ;; 121 ns
  (with-open [snapshot (ip/pooling-snapshot (snapshot))]
    (quick-bench
     (let [i1 (p/-new-iterator snapshot :a)
           i2 (p/-new-iterator snapshot :a)
           i3 (p/-new-iterator snapshot :a)
           i4 (p/-new-iterator snapshot :a)]
       (close i1)
       (close i2)
       (close i3)
       (close i4)))))
