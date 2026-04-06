(ns blaze.jvm-metrics-logger.impl-test
  (:require
   [blaze.jvm-metrics-logger.impl :as impl]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]
   [taoensso.timbre :as log])
  (:import
   [java.lang.management GarbageCollectorMXBean MemoryUsage]
   [java.util Locale]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)
(Locale/setDefault Locale/ROOT)

(test/use-fixtures :each tu/fixture)

(defn- mem-usage ^MemoryUsage [used committed max]
  (MemoryUsage. 0 used committed max))

(defn- mock-gc [gc-name cnt time-ms]
  (reify GarbageCollectorMXBean
    (getName [_] gc-name)
    (getCollectionCount [_] cnt)
    (getCollectionTime [_] time-ms)))

(deftest heap-pct-test
  (testing "returns percentage when max is defined and positive"
    (is (= 50 (impl/heap-pct (mem-usage 500 1000 1000)))))

  (testing "returns 0 when max is -1 (undefined)"
    (is (= 0 (impl/heap-pct (mem-usage 500 1000 -1))))))

(deftest heap-usage-string-test
  (testing "MB format without GC beans"
    (let [heap     (mem-usage (* 512 1048576) (* 1024 1048576) (* 2048 1048576))
          non-heap (mem-usage (* 100 1048576) (* 200 1048576) -1)]
      (is (= "Heap: 512 MB used / 1.0 GB committed / 2.0 GB max (25%), Non-Heap: 100 MB used / 200 MB committed"
             (impl/heap-usage-string heap non-heap [] 25)))))

  (testing "GB format with one GC bean reporting a single collection"
    (let [heap     (mem-usage 1073741824 2147483648 2147483648)
          non-heap (mem-usage 1073741824 2147483648 -1)
          gc       (mock-gc "G1 Young Generation" 1 500)]
      (is (= "Heap: 1.0 GB used / 2.0 GB committed / 2.0 GB max (50%), Non-Heap: 1.0 GB used / 2.0 GB committed, GC: G1 Young Generation 1 collection 0.5s"
             (impl/heap-usage-string heap non-heap [gc] 50)))))

  (testing "with one GC bean reporting multiple collections"
    (let [heap     (mem-usage 500 1000 1000)
          non-heap (mem-usage 100 200 -1)
          gc       (mock-gc "G1 Old Generation" 5 2000)]
      (is (= "Heap: 0 MB used / 0 MB committed / 0 MB max (50%), Non-Heap: 0 MB used / 0 MB committed, GC: G1 Old Generation 5 collections 2.0s"
             (impl/heap-usage-string heap non-heap [gc] 50))))))

(defmacro with-log-capture [captured & body]
  `(log/with-merged-config
     {:appenders {:println {:enabled? false}
                  :capture {:enabled? true
                            :fn (fn [data#]
                                  (reset! ~captured {:level (:level data#)
                                                     :vargs (:vargs data#)}))}}}
     ~@body))

(deftest run-tick-test
  (testing "logs at warn level when heap pct >= warn-threshold"
    (let [captured (atom nil)]
      (with-log-capture captured
        (impl/run-tick! 0 1 (atom 0)))
      (is (= :warn (:level @captured)))
      (is (= "High heap usage -" (first (:vargs @captured))))))

  (testing "logs at debug level on every warn-factor-th tick"
    (let [captured (atom nil)]
      (with-log-capture captured
        (impl/run-tick! 100 1 (atom 0)))
      (is (= :debug (:level @captured)))
      (is (str/starts-with? (first (:vargs @captured)) "Heap:")))))
