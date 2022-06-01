(ns blaze.cassandra.config-test
  (:require
    [blaze.cassandra.config :as config]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest are testing]]
    [java-time :as time])
  (:import
    [com.datastax.oss.driver.api.core.config OptionsMap TypedDriverOption]
    [java.net InetSocketAddress]))


(set! *warn-on-reflection* true)
(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest options-test
  (testing "defaults"
    (let [^OptionsMap options (config/options nil)]
      (are [k v] (= v (.get options k))
        TypedDriverOption/REQUEST_THROTTLER_CLASS
        "ConcurrencyLimitingRequestThrottler"

        TypedDriverOption/REQUEST_THROTTLER_MAX_CONCURRENT_REQUESTS
        1024

        TypedDriverOption/REQUEST_THROTTLER_MAX_QUEUE_SIZE
        100000

        TypedDriverOption/REQUEST_TIMEOUT
        (time/millis 2000))))

  (testing "custom values"
    (let [^OptionsMap options (config/options {:max-concurrent-requests 32
                                               :max-request-queue-size 1000
                                               :request-timeout 5000})]
      (are [k v] (= v (.get options k))
        TypedDriverOption/REQUEST_THROTTLER_CLASS
        "ConcurrencyLimitingRequestThrottler"

        TypedDriverOption/REQUEST_THROTTLER_MAX_CONCURRENT_REQUESTS
        32

        TypedDriverOption/REQUEST_THROTTLER_MAX_QUEUE_SIZE
        1000

        TypedDriverOption/REQUEST_TIMEOUT
        (time/millis 5000)))))


(deftest build-contact-points-test
  (are [s res] (= res (config/build-contact-points s))
    "localhost:9042"
    [(InetSocketAddress. "localhost" 9042)]

    "node-1:9042,node-2:9042"
    [(InetSocketAddress. "node-1" 9042)
     (InetSocketAddress. "node-2" 9042)]))
