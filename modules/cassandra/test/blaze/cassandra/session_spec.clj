(ns blaze.cassandra.session-spec
  (:require
    [blaze.cassandra.session :as session]
    [clojure.spec.alpha :as s])
  (:import
    [com.datastax.oss.driver.api.core CqlSession CqlSessionBuilder]))


(s/fdef session/session-builder
  :args (s/cat :config map?)
  :ret #(instance? CqlSessionBuilder %))


(s/fdef session/build-session
  :args #(instance? CqlSessionBuilder %)
  :ret #(instance? CqlSession %))
