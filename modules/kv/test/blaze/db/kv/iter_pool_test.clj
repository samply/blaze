(ns blaze.db.kv.iter-pool-test
  (:require
   [blaze.anomaly :as ba :refer [throw-anom]]
   [blaze.async.comp :as ac]
   [blaze.db.kv :as kv]
   [blaze.db.kv-spec]
   [blaze.db.kv.iter-pool :as ip]
   [blaze.db.kv.iter-pool-spec]
   [blaze.db.kv.protocols :as p]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [blaze.db.kv KvCloseable]
   [blaze.db.kv.iter_pool Pool Pools PooledIterator PoolingSnapshot]
   [java.util.concurrent CountDownLatch Executors ExecutorService TimeUnit]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def ^:private iterator-closed-anom
  (ba/fault "The iterator is closed."))

(deftype Iterator [column-family num-closes]
  p/KvIterator
  (-valid [_]
    (when (pos? @num-closes) (throw-anom iterator-closed-anom))
    false)
  KvCloseable
  (close [_]
    (swap! num-closes inc)))

(defn- num-open-iterators [{:keys [iterators]}]
  (count (for [[_ iters] iterators
               ^Iterator iter iters
               :when (zero? @(.-num-closes iter))]
           iter)))

(deftype Snapshot [state]
  p/KvSnapshot
  (-new-iterator [_ column-family]
    (when (= :unknown column-family)
      (throw-anom (ba/not-found "Column family `unknown` not found.")))
    (-> (swap! state
               (fn [{:keys [num-closes] :as state}]
                 (cond-> (update-in state [:iterators column-family] conj
                                    (->Iterator column-family (atom 0)))
                   (pos? num-closes)
                   (assoc :created-after-close? true))))
        :iterators column-family first))

  KvCloseable
  (close [_]
    (swap! state
           (fn [state]
             (-> (update state :num-closes inc)
                 (assoc :num-open-iterators-on-close
                        (num-open-iterators state)))))))

(defn- snapshot []
  (->Snapshot (atom {:num-closes 0})))

(defn- pools ^Pools [pooling-snapshot]
  (.-pools ^PoolingSnapshot pooling-snapshot))

(defn- curr-pools
  "Returns a map of thread to pool of all pools of `pooling-snapshot`."
  [pooling-snapshot]
  (into {} (.pools (pools pooling-snapshot))))

(defn- curr-pool
  "Returns the pool of the current thread of `pooling-snapshot`."
  ^Pool [pooling-snapshot]
  (get (curr-pools pooling-snapshot) (Thread/currentThread)))

(defn- num-borrowed [^Pool pool]
  (.numBorrowed pool))

(defn- returned [^Pool pool]
  (update-vals (into {} (.returned pool)) vec))

(defn- pool-closed? [^Pool pool]
  (.isClosed pool))

(defn- orig-iter [pooled-iterator]
  (.-iter ^PooledIterator pooled-iterator))

(defn- num-orig-iter-closes [pooled-iterator]
  @(.-num-closes ^Iterator (orig-iter pooled-iterator)))

(defn- orig-iter-closed? [pooled-iterator]
  (pos? (num-orig-iter-closes pooled-iterator)))

(defn- orig-snapshot-state [snapshot]
  @(.-state ^Snapshot snapshot))

(defn- num-orig-snapshot-closes [snapshot]
  (:num-closes (orig-snapshot-state snapshot)))

(defn- all-orig-iters-closed-once? [snapshot]
  (every?
   #(= 1 @(.-num-closes ^Iterator %))
   (mapcat val (:iterators (orig-snapshot-state snapshot)))))

(defn- close [closeable]
  (.close ^KvCloseable closeable))

(defn- curr-orig-iter-column-family [pooled-iterator]
  (.-column-family ^Iterator (orig-iter pooled-iterator)))

(defn- iterator-closed-anom? [anom]
  (and (ba/fault? anom) (= "The iterator is closed." (::anom/message anom))))

(defn- snapshot-closed-anom? [anom]
  (and (ba/fault? anom) (= "The snapshot is closed." (::anom/message anom))))

(defn- run-on
  "Runs `f` on a thread of `executor` and returns its result."
  [^ExecutorService executor f]
  (.get (.submit executor ^Callable f)))

(defn- new-iterator-on
  "Borrows an iterator of `column-family` from `snapshot` on a thread of
  `executor`. Returns a tuple of the iterator and the thread."
  [executor snapshot column-family]
  (run-on executor #(vector (p/-new-iterator snapshot column-family)
                            (Thread/currentThread))))

(defn- borrow-and-return
  "Borrows an iterator, uses and returns it.

  Returns :ok on success and :closed if the snapshot was closed before. Returns
  :raced if the snapshot was closed while the iterator was used, which the
  original iterators of this namespace detect, but RocksDB iterators don't."
  [snapshot column-family]
  (let [res (ba/try-anomaly
             (let [iter (p/-new-iterator snapshot column-family)]
               (kv/valid? iter)
               (close iter)
               :ok))]
    (cond
      (snapshot-closed-anom? res) :closed
      (iterator-closed-anom? res) :raced
      :else res)))

(deftest new-iterator-test
  (testing "borrowing and returning one iterator"
    (let [orig-snapshot (snapshot)
          snapshot (ip/pooling-snapshot orig-snapshot)
          pooled-iter (p/-new-iterator snapshot :a)]

      (testing "the pool of the current thread is created"
        (is (= 1 (count (curr-pools snapshot)))))

      (testing "pool state after borrowing an iterator"
        (given (curr-pool snapshot)
          num-borrowed := 1
          returned := {}))

      (testing "original iterator column family"
        (is (= :a (curr-orig-iter-column-family pooled-iter))))

      (testing "iterator can be used"
        (is (false? (kv/valid? pooled-iter))))

      (close pooled-iter)

      (testing "errors on closed iterator"
        (is (iterator-closed-anom? (ba/try-anomaly (kv/valid? pooled-iter)))))

      (testing "pool state after returning the iterator"
        (given (curr-pool snapshot)
          num-borrowed := 0
          returned := {:a [(orig-iter pooled-iter)]}))

      (testing "original iterator isn't closed after returning it"
        (is (false? (orig-iter-closed? pooled-iter))))

      (close snapshot)

      (testing "original iterator is closed after closing the snapshot"
        (is (= 1 (num-orig-iter-closes pooled-iter))))

      (testing "original snapshot is closed exactly once"
        (is (= 1 (num-orig-snapshot-closes orig-snapshot))))))

  (testing "borrowing and returning two iterators"
    (let [orig-snapshot (snapshot)
          snapshot (ip/pooling-snapshot orig-snapshot)
          pooled-iter-1 (p/-new-iterator snapshot :a)
          pooled-iter-2 (p/-new-iterator snapshot :a)]

      (testing "pool state after borrowing two iterators"
        (given (curr-pool snapshot)
          num-borrowed := 2
          returned := {}))

      (testing "iterators can be used"
        (is (false? (kv/valid? pooled-iter-1)))
        (is (false? (kv/valid? pooled-iter-2))))

      (close pooled-iter-1)
      (close pooled-iter-2)

      (testing "pool state after returning the iterators"
        (given (curr-pool snapshot)
          num-borrowed := 0
          returned := {:a [(orig-iter pooled-iter-1) (orig-iter pooled-iter-2)]}))

      (close snapshot)

      (testing "original iterators are closed after closing the snapshot"
        (is (= 1 (num-orig-iter-closes pooled-iter-1)))
        (is (= 1 (num-orig-iter-closes pooled-iter-2))))

      (testing "original snapshot is closed exactly once"
        (is (= 1 (num-orig-snapshot-closes orig-snapshot))))))

  (testing "re-borrowing an iterator"
    (with-open [snapshot (ip/pooling-snapshot (snapshot))]
      (let [pooled-iter-1 (p/-new-iterator snapshot :a)
            _ (close pooled-iter-1)
            pooled-iter-2 (p/-new-iterator snapshot :a)]

        (testing "the returned iterator is borrowed again"
          (given (curr-pool snapshot)
            num-borrowed := 1
            returned := {:a []})
          (is (identical? (orig-iter pooled-iter-1) (orig-iter pooled-iter-2))))

        (testing "iterator can be used"
          (is (false? (kv/valid? pooled-iter-2))))

        (close pooled-iter-2))))

  (testing "borrowing from two column families"
    (with-open [snapshot (ip/pooling-snapshot (snapshot))]
      (let [pooled-iter-a (p/-new-iterator snapshot :a)]
        (close pooled-iter-a)

        (let [pooled-iter-b (p/-new-iterator snapshot :b)]

          (testing "pool state"
            (given (curr-pool snapshot)
              num-borrowed := 1
              returned := {:a [(orig-iter pooled-iter-a)]}))

          (testing "original iterator column family"
            (is (= :b (curr-orig-iter-column-family pooled-iter-b))))

          (close pooled-iter-b)))))

  (testing "returning an iterator twice has no effect"
    (with-open [snapshot (ip/pooling-snapshot (snapshot))]
      (let [pooled-iter (p/-new-iterator snapshot :a)]
        (close pooled-iter)
        (close pooled-iter)

        (given (curr-pool snapshot)
          num-borrowed := 0
          returned := {:a [(orig-iter pooled-iter)]}))))

  (testing "closing an iterator again doesn't return the iterator borrowed again in between"
    (with-open [snapshot (ip/pooling-snapshot (snapshot))]
      (let [pooled-iter-1 (p/-new-iterator snapshot :a)
            _ (close pooled-iter-1)
            pooled-iter-2 (p/-new-iterator snapshot :a)]
        (is (identical? (orig-iter pooled-iter-1) (orig-iter pooled-iter-2)))

        (close pooled-iter-1)

        (given (curr-pool snapshot)
          num-borrowed := 1
          returned := {:a []})

        (close pooled-iter-2))))

  (testing "failing to create an iterator doesn't count as borrowed"
    (with-open [snapshot (ip/pooling-snapshot (snapshot))]
      (given (ba/try-anomaly (p/-new-iterator snapshot :unknown))
        ::anom/category := ::anom/not-found)

      (given (curr-pool snapshot)
        num-borrowed := 0))))

(deftest close-test
  (testing "the snapshot is closed if it was never used"
    (let [orig-snapshot (snapshot)
          snapshot (ip/pooling-snapshot orig-snapshot)]
      (close snapshot)

      (is (= 1 (num-orig-snapshot-closes orig-snapshot)))))

  (testing "close is idempotent"
    (let [orig-snapshot (snapshot)
          snapshot (ip/pooling-snapshot orig-snapshot)
          pooled-iter (p/-new-iterator snapshot :a)]
      (close snapshot)
      (close snapshot)

      (is (= 1 (num-orig-snapshot-closes orig-snapshot)))
      (is (= 1 (num-orig-iter-closes pooled-iter)))))

  (testing "close closes borrowed iterators"
    (let [orig-snapshot (snapshot)
          snapshot (ip/pooling-snapshot orig-snapshot)
          pooled-iter-1 (p/-new-iterator snapshot :a)
          pooled-iter-2 (p/-new-iterator snapshot :b)]

      (close snapshot)

      (testing "the original iterators are closed"
        (is (= 1 (num-orig-iter-closes pooled-iter-1)))
        (is (= 1 (num-orig-iter-closes pooled-iter-2))))

      (testing "the original snapshot is closed exactly once after its iterators"
        (given (orig-snapshot-state orig-snapshot)
          :num-closes := 1
          :num-open-iterators-on-close := 0))

      (testing "the pool is closed and holds no iterators anymore"
        (given (curr-pool snapshot)
          pool-closed? := true
          num-borrowed := 0
          returned := {}))

      (testing "using a borrowed iterator throws a fault"
        (is (snapshot-closed-anom? (ba/try-anomaly (kv/valid? pooled-iter-1))))
        (is (snapshot-closed-anom? (ba/try-anomaly (kv/valid? pooled-iter-2)))))

      (testing "returning the borrowed iterators has no effect"
        (close pooled-iter-1)
        (close pooled-iter-2)

        (given (curr-pool snapshot)
          num-borrowed := 0
          returned := {})

        (is (= 1 (num-orig-iter-closes pooled-iter-1)))
        (is (= 1 (num-orig-iter-closes pooled-iter-2))))

      (testing "using a returned iterator throws the iterator closed fault"
        (is (iterator-closed-anom? (ba/try-anomaly (kv/valid? pooled-iter-1)))))))

  (testing "every method of a borrowed iterator throws a fault after close"
    (let [snapshot (ip/pooling-snapshot (snapshot))
          pooled-iter (p/-new-iterator snapshot :a)]
      (close snapshot)

      (doseq [f [p/-valid p/-seek-to-first p/-seek-to-last #(p/-seek % nil)
                 #(p/-seek-buffer % nil) #(p/-seek-for-prev % nil)
                 #(p/-seek-for-prev-buffer % nil) p/-next p/-prev p/-key
                 #(p/-key % nil) p/-value #(p/-value % nil)]]
        (is (snapshot-closed-anom? (ba/try-anomaly (f pooled-iter)))))))

  (testing "close closes returned and borrowed iterators"
    (let [orig-snapshot (snapshot)
          snapshot (ip/pooling-snapshot orig-snapshot)
          pooled-iter-1 (p/-new-iterator snapshot :a)
          pooled-iter-2 (p/-new-iterator snapshot :a)]

      (close pooled-iter-1)
      (close snapshot)

      (is (= 1 (num-orig-iter-closes pooled-iter-1)))
      (is (= 1 (num-orig-iter-closes pooled-iter-2)))

      (given (orig-snapshot-state orig-snapshot)
        :num-closes := 1
        :num-open-iterators-on-close := 0)))

  (testing "close closes the iterators of all pools"
    (let [executor (Executors/newSingleThreadExecutor)
          orig-snapshot (snapshot)
          snapshot (ip/pooling-snapshot orig-snapshot)
          pooled-iter-1 (p/-new-iterator snapshot :a)
          [pooled-iter-2] (new-iterator-on executor snapshot :a)]

      (is (= 2 (count (curr-pools snapshot))))

      (close snapshot)

      (testing "all pools are closed"
        (is (every? pool-closed? (vals (curr-pools snapshot)))))

      (testing "the original iterators are closed"
        (is (= 1 (num-orig-iter-closes pooled-iter-1)))
        (is (= 1 (num-orig-iter-closes pooled-iter-2))))

      (testing "the original snapshot is closed exactly once after its iterators"
        (given (orig-snapshot-state orig-snapshot)
          :num-closes := 1
          :num-open-iterators-on-close := 0))

      (testing "using the iterator on its thread throws a fault"
        (is (snapshot-closed-anom?
             (run-on executor #(ba/try-anomaly (kv/valid? pooled-iter-2))))))

      (testing "returning the iterator on its thread has no effect"
        (run-on executor #(close pooled-iter-2))
        (is (= 1 (num-orig-iter-closes pooled-iter-2))))

      (.shutdown executor))))

(deftest new-iterator-after-close-test
  (testing "borrowing after close throws a fault"
    (let [orig-snapshot (snapshot)
          snapshot (ip/pooling-snapshot orig-snapshot)]
      (close (p/-new-iterator snapshot :a))
      (close snapshot)

      (is (snapshot-closed-anom? (ba/try-anomaly (p/-new-iterator snapshot :a))))

      (testing "no iterator is created after close"
        (is (= 1 (count (get-in (orig-snapshot-state orig-snapshot) [:iterators :a]))))
        (is (not (:created-after-close? (orig-snapshot-state orig-snapshot)))))))

  (testing "borrowing after close throws a fault if the snapshot was never used"
    (let [snapshot (ip/pooling-snapshot (snapshot))]
      (close snapshot)

      (is (snapshot-closed-anom? (ba/try-anomaly (p/-new-iterator snapshot :a))))

      (testing "no pool is created"
        (is (empty? (curr-pools snapshot))))))

  (testing "borrowing after close throws a fault on a thread without pool"
    (let [executor (Executors/newSingleThreadExecutor)
          snapshot (ip/pooling-snapshot (snapshot))]
      (close (p/-new-iterator snapshot :a))
      (close snapshot)

      (is (snapshot-closed-anom?
           (run-on executor #(ba/try-anomaly (p/-new-iterator snapshot :a)))))

      (testing "no pool is created"
        (is (= [(Thread/currentThread)] (keys (curr-pools snapshot)))))

      (.shutdown executor))))

(deftest null-argument-test
  (testing "the snapshot can't be nil"
    (is (thrown? NullPointerException (Pools. nil p/-new-iterator))))

  (testing "the function creating iterators can't be nil"
    (is (thrown? NullPointerException (Pools. (snapshot) nil))))

  (let [orig-snapshot (snapshot)
        pools (Pools. orig-snapshot p/-new-iterator)
        pool (.pool pools)]

    (testing "the column family of a borrowed iterator can't be nil"
      (is (thrown? NullPointerException (.borrow pool nil)))

      (testing "no iterator is created"
        (is (empty? (:iterators (orig-snapshot-state orig-snapshot))))))

    (testing "the column family of a given back iterator can't be nil"
      (is (thrown? NullPointerException (.giveBack pool nil (.borrow pool :a)))))

    (.close pools)

    (testing "a given back iterator can't be nil, even if the pool is closed"
      (is (thrown? NullPointerException (.giveBack pool :a nil))))))

(deftest concurrent-close-test
  (testing "closing the same iterator from two threads at once returns it exactly once"
    (let [executor (Executors/newFixedThreadPool 2)]
      (dotimes [_ 1000]
        (with-open [snapshot (ip/pooling-snapshot (snapshot))]
          (let [pooled-iter (p/-new-iterator snapshot :a)
                start (CountDownLatch. 1)
                futures (mapv
                         (fn [_]
                           (ac/supply-async
                            (fn []
                              (.await start)
                              (close pooled-iter))
                            executor))
                         (range 2))]
            (.countDown start)
            @(ac/all-of futures)

            (given (curr-pool snapshot)
              num-borrowed := 0
              returned := {:a [(orig-iter pooled-iter)]}))))
      (.shutdown executor))))

(deftest return-from-other-thread-test
  (testing "an iterator returned from another thread goes back to the pool it was borrowed from"
    (let [executor (Executors/newSingleThreadExecutor)]
      (with-open [snapshot (ip/pooling-snapshot (snapshot))]
        (let [[pooled-iter thread] (new-iterator-on executor snapshot :a)]

          (close pooled-iter)

          (testing "the current thread has no pool"
            (is (nil? (curr-pool snapshot))))

          (testing "the iterator is returned to the pool of the borrowing thread"
            (given (get (curr-pools snapshot) thread)
              num-borrowed := 0
              returned := {:a [(orig-iter pooled-iter)]}))

          (testing "the borrowing thread re-borrows the iterator"
            (let [[pooled-iter-2] (new-iterator-on executor snapshot :a)]
              (is (identical? (orig-iter pooled-iter) (orig-iter pooled-iter-2)))
              (close pooled-iter-2)))))
      (.shutdown executor))))

(deftest concurrency-test
  (testing "borrowing and returning many iterators from 100 threads in parallel"
    (let [num-threads 100
          executor (Executors/newFixedThreadPool num-threads)
          orig-snapshot (snapshot)
          snapshot (ip/pooling-snapshot orig-snapshot)
          futures (mapv
                   (fn [column-family]
                     (ac/supply-async
                      #(close (p/-new-iterator snapshot column-family))
                      executor))
                   (flatten (repeat 10000 [:a :b :c])))]

      ;; wait on all futures
      @(ac/all-of futures)

      (testing "pool states"
        (is (<= (count (curr-pools snapshot)) num-threads))
        (doseq [pool (vals (curr-pools snapshot))]
          (given pool
            num-borrowed := 0
            [returned :a count] :? #(<= (or % 0) 1)
            [returned :b count] :? #(<= (or % 0) 1)
            [returned :c count] :? #(<= (or % 0) 1))))

      (close snapshot)

      (testing "the original snapshot is closed exactly once after its iterators"
        (given (orig-snapshot-state orig-snapshot)
          :num-closes := 1
          :num-open-iterators-on-close := 0))

      (testing "all original iterators are closed exactly once"
        (is (all-orig-iters-closed-once? orig-snapshot)))

      (.shutdown executor)))

  (testing "closing while many threads borrow and return iterators"
    (dotimes [_ 100]
      (let [num-threads 8
            executor (Executors/newFixedThreadPool num-threads)
            orig-snapshot (snapshot)
            snapshot (ip/pooling-snapshot orig-snapshot)
            start (CountDownLatch. 1)
            futures (mapv
                     (fn [column-family]
                       (ac/supply-async
                        (fn []
                          (.await start)
                          (borrow-and-return snapshot column-family))
                        executor))
                     (flatten (repeat 100 [:a :b])))]

        (.countDown start)
        (close snapshot)

        @(ac/all-of futures)

        (testing "every borrow either succeeded, failed with a fault or raced with close"
          (is (every? #{:ok :closed :raced} (map deref futures))))

        (testing "the original snapshot is closed exactly once after its iterators"
          (given (orig-snapshot-state orig-snapshot)
            :num-closes := 1
            :num-open-iterators-on-close := 0
            :created-after-close? := nil))

        (testing "all original iterators are closed exactly once"
          (is (all-orig-iters-closed-once? orig-snapshot)))

        (.shutdown executor)
        (.awaitTermination executor 10 TimeUnit/SECONDS)))))

(comment
  (require '[criterium.core :refer [bench quick-bench]])
  (st/unstrument)

  (with-open [snapshot (ip/pooling-snapshot (snapshot))]
    (quick-bench (close (p/-new-iterator snapshot :a))))

  (with-open [snapshot (ip/pooling-snapshot (snapshot))]
    (quick-bench
     (let [i1 (p/-new-iterator snapshot :a)
           i2 (p/-new-iterator snapshot :a)]
       (close i1)
       (close i2))))

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
