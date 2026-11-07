(ns blaze.db.impl.fold-test
  (:require
   [blaze.db.impl.fold :as f]
   [blaze.db.impl.fold-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]])
  (:import
   [clojure.lang IReduceInit]
   [java.util.concurrent CountDownLatch ForkJoinPool ForkJoinTask TimeUnit]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private pool
  "A pool of its own, so that its workers can be told apart from the ones of
  the common pool."
  (ForkJoinPool. 4))

(defn- thread-name []
  (.getName (Thread/currentThread)))

(defn- current-pool
  "Returns the ForkJoinPool of the calling thread or nil if it doesn't belong
  to one."
  []
  (ForkJoinTask/getPool))

(defn- await-other-part
  "Counts `latch` down and waits for the other part to do the same, which only
  succeeds if both parts are reduced at the same time."
  [^CountDownLatch latch _]
  (.countDown latch)
  [(.await latch 10 TimeUnit/SECONDS)])

(defn- fail-first-part
  "Throws for the part starting with 0 and records the part starting with 1 in
  `done` after some time, so that it finishes after the failing one."
  [done [x :as part]]
  (condp = x
    0 (throw (ex-info "part 0 failed" {}))
    1 (do (Thread/sleep 200)
          (swap! done conj x)
          [part])
    [part]))

(defn- coll-waiting-after-first
  "Returns a reducible collection of 0 and 1 that waits after 0 until `started`
  is counted down, at most 10 seconds, recording in `started-in-time` whether
  it was."
  [^CountDownLatch started started-in-time]
  (reify IReduceInit
    (reduce [_ rf init]
      (let [acc (rf init 0)]
        (reset! started-in-time (.await started 10 TimeUnit/SECONDS))
        (rf acc 1)))))

(defn- count-down-on-start
  "Counts `started` down and returns `part` in a vector."
  [^CountDownLatch started part]
  (.countDown started)
  [part])

(deftest fold-test
  (testing "an empty collection has no parts and results in (combine)"
    (is (= [] (f/fold pool 2 vector into []))))

  (testing "a collection with fewer than n elements is a single part reduced on
            the calling thread"
    (is (= [[[1] (thread-name)]]
           (f/fold pool 2 (fn [part] [[part (thread-name)]]) into [1]))))

  (testing "the parts have n elements, except the last one, and their results
            are combined in order"
    (is (= [[0 1] [2 3] [4 5] [6]] (f/fold pool 2 vector into (vec (range 7)))))
    (is (= [[0 1] [2 3]] (f/fold pool 2 vector into (vec (range 4))))))

  (testing "a collection with a multiple of n elements combines only the
            results of its parts"
    (is (= 4 (f/fold pool 2 count + (vec (range 4))))))

  (testing "the parts of n elements are reduced on pool"
    (is (= [pool pool]
           (f/fold pool 1 (fn [_] [(current-pool)]) into [0 1]))))

  (testing "the parts are reduced in parallel"
    (let [latch (CountDownLatch. 2)]
      (is (= [true true]
             (f/fold pool 1 (partial await-other-part latch) into [0 1])))))

  (testing "a part is reduced while the collection is still read"
    (let [started (CountDownLatch. 1)
          started-in-time (atom nil)]
      (is (= [[0] [1]]
             (f/fold pool 1 (partial count-down-on-start started) into
                     (coll-waiting-after-first started started-in-time))))
      (is (true? @started-in-time))))

  (testing "a failing part fails the fold only after all parts are done"
    (let [done (atom #{})]
      (is (thrown-with-msg?
           Exception #"part 0 failed"
           (f/fold pool 1 (partial fail-first-part done) into [0 1 2])))
      (is (= #{1} @done))))

  (testing "a failing last part, reduced on the calling thread, fails the fold
            only after all forked parts are done"
    (let [done (atom #{})]
      (is (thrown-with-msg?
           Exception #"part 0 failed"
           (f/fold pool 2 (partial fail-first-part done) into [1 2 0])))
      (is (= #{1} @done))))

  (testing "failing to read the collection fails the fold only after all forked
            parts are done"
    (let [done (atom #{})]
      (is (thrown-with-msg?
           Exception #"read failed"
           (f/fold pool 1 (partial fail-first-part done) into
                   (reify IReduceInit
                     (reduce [_ rf init]
                       (rf init 1)
                       (throw (ex-info "read failed" {})))))))
      (is (= #{1} @done)))))

(defn- fail-first-call
  "Throws for 0 and records 1 in `done` after some time, so that it finishes
  after the failing call."
  [done x]
  (condp = x
    0 (throw (ex-info "call 0 failed" {}))
    1 (do (Thread/sleep 200)
          (swap! done conj x))
    nil))

(deftest run-parallel!-test
  (testing "an empty collection calls nothing"
    (is (nil? (f/run-parallel! pool #(throw (ex-info "called" {:x %})) []))))

  (testing "calls f with each element and returns nil"
    (let [calls (atom #{})]
      (is (nil? (f/run-parallel! pool #(swap! calls conj %) [0 1 2])))
      (is (= #{0 1 2} @calls))))

  (testing "calls f on pool"
    (let [pools (atom [])]
      (f/run-parallel! pool (fn [_] (swap! pools conj (current-pool))) [0 1])
      (is (= [pool pool] @pools))))

  (testing "calls f in parallel"
    (let [latch (CountDownLatch. 2)
          awaited (atom [])]
      (f/run-parallel! pool #(swap! awaited conj (first (await-other-part latch %)))
                       [0 1])
      (is (= [true true] @awaited))))

  (testing "a failing call fails the run only after all calls are done"
    (let [done (atom #{})]
      (is (thrown-with-msg?
           Exception #"call 0 failed"
           (f/run-parallel! pool (partial fail-first-call done) [0 1 2])))
      (is (= #{1} @done)))))
