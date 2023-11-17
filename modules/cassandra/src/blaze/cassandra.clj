(ns blaze.cassandra
  (:require
   [blaze.anomaly :as ba]
   [blaze.cassandra.session :as session]
   [clojure.string :as str]
   [cognitect.anomalies :as anom])
  (:import
   [com.datastax.oss.driver.api.core
    CqlSession DriverTimeoutException RequestThrottlingException]
   [com.datastax.oss.driver.api.core.cql
    AsyncResultSet PreparedStatement Row Statement SimpleStatement]
   [com.datastax.oss.driver.api.core.servererrors WriteTimeoutException]))

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

(defn session [config]
  (-> config session/session-builder session/build-session))

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
