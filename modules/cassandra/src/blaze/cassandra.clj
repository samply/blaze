(ns blaze.cassandra
  (:require
    [blaze.anomaly :as ba]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [java-time :as time])
  (:import
    [com.datastax.oss.driver.api.core
     CqlSession DriverTimeoutException RequestThrottlingException]
    [com.datastax.oss.driver.api.core.config OptionsMap TypedDriverOption]
    [com.datastax.oss.driver.api.core.cql
     AsyncResultSet PreparedStatement Row Statement SimpleStatement]
    [com.datastax.oss.driver.api.core.servererrors WriteTimeoutException]
    [com.datastax.oss.driver.api.core.config DriverConfigLoader]
    [java.net InetSocketAddress]))


(set! *warn-on-reflection* true)


(defn prepare [session statement]
  (.prepare ^CqlSession session ^SimpleStatement statement))


(defn bind [prepared-statement & params]
  (.bind ^PreparedStatement prepared-statement (object-array params)))


(defn execute [session statement]
  (.executeAsync ^CqlSession session ^Statement statement))


(defn first-row [result-set]
  (if-let [^Row row (.one ^AsyncResultSet result-set)]
    (.array (.getByteBuffer row 0))
    {::anom/category ::anom/not-found}))


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


(defn session
  [{:keys [contact-points username password key-space]
    :or {contact-points "localhost:9042" key-space "blaze"
         username "cassandra" password "cassandra"}}
   options]
  (-> (CqlSession/builder)
      (.withConfigLoader (DriverConfigLoader/fromMap options))
      (.addContactPoints (build-contact-points contact-points))
      (.withAuthCredentials username password)
      (.withLocalDatacenter "datacenter1")
      (.withKeyspace ^String key-space)
      (.build)))


(defn- format-value [k v]
  (if (str/ends-with? k "password") "[hidden]" v))


(defn format-config [config]
  (->> config
       (keep
         (fn [[k v]] (when (some? v) (str (name k) " = " (format-value k v)))))
       (str/join ", ")))


(defn close [session]
  (.close ^CqlSession session))


(extend-protocol ba/ToAnomaly
  DriverTimeoutException
  (-anomaly [e]
    (ba/busy (str "Cassandra " (ex-message e))))
  WriteTimeoutException
  (-anomaly [e]
    (ba/busy (ex-message e)))
  RequestThrottlingException
  (-anomaly [e]
    (ba/busy (str "Cassandra " (ex-message e)))))
