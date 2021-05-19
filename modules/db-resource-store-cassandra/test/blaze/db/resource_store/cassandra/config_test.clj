(ns blaze.db.resource-store.cassandra.config-test
  (:require
    [blaze.db.resource-store.cassandra.config :as c]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest are testing]]
    [java-time :as jt])
  (:import
    [com.datastax.oss.driver.api.core.config OptionsMap TypedDriverOption]
    [java.net InetSocketAddress]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest options-test
  (testing "defaults"
    (let [^OptionsMap options (c/options nil)]
      (are [k v] (= v (.get options k))
        TypedDriverOption/REQUEST_THROTTLER_CLASS
        "ConcurrencyLimitingRequestThrottler"

        TypedDriverOption/REQUEST_THROTTLER_MAX_CONCURRENT_REQUESTS
        1024

        TypedDriverOption/REQUEST_THROTTLER_MAX_QUEUE_SIZE
        100000

        TypedDriverOption/REQUEST_TIMEOUT
        (jt/millis 2000))))

  (testing "custom values"
    (let [^OptionsMap options (c/options {:max-concurrent-requests 32
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
        (jt/millis 5000)))))


(deftest build-contact-points-test
  (are [s res] (= res (c/build-contact-points s))
    "localhost:9042"
    [(InetSocketAddress. "localhost" 9042)]

    "node-1:9042,node-2:9042"
    [(InetSocketAddress. "node-1" 9042)
     (InetSocketAddress. "node-2" 9042)]))
