(ns blaze.cassandra.session
  (:require
    [blaze.cassandra.config :as config])
  (:import
    [com.datastax.oss.driver.api.core CqlSession  ]
    [com.datastax.oss.driver.api.core.config DriverConfigLoader]
    [com.datastax.oss.driver.api.core.session SessionBuilder]))


(set! *warn-on-reflection* true)


(defn session-builder
  {:arglists '([config])}
  [{:keys [contact-points username password key-space]
    :or {contact-points "localhost:9042" key-space "blaze"
         username "cassandra" password "cassandra"}
    :as config}]
  (-> (CqlSession/builder)
      (.withConfigLoader (DriverConfigLoader/fromMap (config/options config)))
      (.addContactPoints (config/build-contact-points contact-points))
      (.withAuthCredentials username password)
      (.withLocalDatacenter "datacenter1")
      (.withKeyspace ^String key-space)))


(defn build-session [session-builder]
  (.build ^SessionBuilder session-builder))
