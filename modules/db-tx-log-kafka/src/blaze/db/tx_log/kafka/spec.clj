(ns blaze.db.tx-log.kafka.spec
  (:require
   [blaze.db.tx-log.kafka :as-alias kafka]
   [blaze.executors :as ex]
   [clojure.spec.alpha :as s]))

(s/def ::kafka/bootstrap-servers
  string?)

(s/def ::kafka/topic
  string?)

(s/def ::kafka/last-t-executor
  ex/executor?)

(s/def ::kafka/max-request-size
  nat-int?)

(s/def ::kafka/compression-type
  #{"none" "gzip" "snappy" "lz4" "zstd"})

(s/def ::kafka/security-protocol
  #{"PLAINTEXT" "SSL"})

(s/def ::kafka/truststore-location
  string?)

(s/def ::kafka/truststore-password
  string?)

(s/def ::kafka/keystore-location
  string?)

(s/def ::kafka/keystore-password
  string?)

(s/def ::kafka/key-password
  string?)
