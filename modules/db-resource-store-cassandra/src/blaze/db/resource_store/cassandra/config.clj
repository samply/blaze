(ns blaze.db.resource-store.cassandra.config
  (:require
    [clojure.string :as str])
  (:import
    [com.datastax.oss.driver.api.core.config OptionsMap TypedDriverOption]
    [java.net InetSocketAddress]))


(set! *warn-on-reflection* true)


(defn options
  [{:keys [max-concurrent-read-requests max-read-request-queue-size]
    :or {max-concurrent-read-requests 1024
         max-read-request-queue-size 100000}}]
  (doto (OptionsMap/driverDefaults)
    (.put TypedDriverOption/REQUEST_THROTTLER_CLASS "ConcurrencyLimitingRequestThrottler")
    (.put TypedDriverOption/REQUEST_THROTTLER_MAX_CONCURRENT_REQUESTS (int max-concurrent-read-requests))
    (.put TypedDriverOption/REQUEST_THROTTLER_MAX_QUEUE_SIZE (int max-read-request-queue-size))))


(defn build-contact-points [contact-points]
  (map
    #(let [[hostname port] (str/split % #":" 2)]
       (InetSocketAddress. ^String hostname (Integer/parseInt port)))
    (str/split contact-points #",")))
