(ns blaze.cassandra.config
  (:require
   [clojure.string :as str]
   [java-time.api :as time])
  (:import
   [com.datastax.oss.driver.api.core.config OptionsMap TypedDriverOption]
   [java.net InetSocketAddress]))

(set! *warn-on-reflection* true)

(defn options
  [{:keys [max-concurrent-requests max-request-queue-size
           request-timeout]
    :or {max-concurrent-requests 1024
         max-request-queue-size 100000
         request-timeout 2000}}]
  (doto (OptionsMap/driverDefaults)
    (.put TypedDriverOption/REQUEST_THROTTLER_CLASS "ConcurrencyLimitingRequestThrottler")
    (.put TypedDriverOption/REQUEST_THROTTLER_MAX_CONCURRENT_REQUESTS (int max-concurrent-requests))
    (.put TypedDriverOption/REQUEST_THROTTLER_MAX_QUEUE_SIZE (int max-request-queue-size))
    (.put TypedDriverOption/REQUEST_TIMEOUT (time/millis request-timeout))))

(defn build-contact-points [contact-points]
  (map
   #(let [[hostname port] (str/split % #":" 2)]
      (InetSocketAddress. ^String hostname (Integer/parseInt port)))
   (str/split contact-points #",")))
