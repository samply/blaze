(ns blaze.cassandra.spec
  (:require
    [clojure.spec.alpha :as s])
  (:import
    [com.datastax.oss.driver.api.core DefaultConsistencyLevel]
    [java.util EnumSet]))


(s/def :blaze.cassandra/contact-points
  (s/and string? #(re-matches #"[^:]+:\d+(,[^:]+:\d+)*" %)))


(s/def :blaze.cassandra/username
  string?)


(s/def :blaze.cassandra/password
  string?)


(s/def :blaze.cassandra/key-space
  string?)


(s/def :blaze.cassandra/put-consistency-level
  (into #{} (map #(.name %)) (EnumSet/allOf DefaultConsistencyLevel)))


(s/def :blaze.cassandra/max-concurrent-read-requests
  nat-int?)


(s/def :blaze.cassandra/max-read-request-queue-size
  nat-int?)


;; in milliseconds
(s/def :blaze.cassandra/request-timeout
  pos-int?)
