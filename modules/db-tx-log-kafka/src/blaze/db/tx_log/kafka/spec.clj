(ns blaze.db.tx-log.kafka.spec
  (:require
    [blaze.executors :as ex]
    [clojure.spec.alpha :as s]))


(s/def :blaze.db.tx-log.kafka/bootstrap-servers
  string?)


(s/def :blaze.db.tx-log.kafka/last-t-executor
  ex/executor?)


(s/def :blaze.db.tx-log.kafka/max-request-size
  nat-int?)


(s/def :blaze.db.tx-log.kafka/compression-type
  #{"none" "gzip" "snappy" "lz4" "zstd"})


(s/def :blaze.db.tx-log.kafka/security-protocol
  #{"PLAINTEXT" "SSL"})


(s/def :blaze.db.tx-log.kafka/truststore-location
  string?)


(s/def :blaze.db.tx-log.kafka/truststore-password
  string?)


(s/def :blaze.db.tx-log.kafka/keystore-location
  string?)


(s/def :blaze.db.tx-log.kafka/keystore-password
  string?)


(s/def :blaze.db.tx-log.kafka/key-password
  string?)
