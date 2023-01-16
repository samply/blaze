(ns blaze.db.tx-log.kafka.spec
  (:require
    [blaze.db.tx-log.spec]
    [blaze.executors :as ex]
    [blaze.fhir.hash.spec]
    [blaze.fhir.spec.spec]
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


(defmulti tx-cmd "Transaction command" :op)


(defmethod tx-cmd "create" [_]
  (s/keys :req-un [:blaze.db.tx-cmd/op
                   :blaze.db.tx-cmd/type
                   :blaze.resource/id
                   :blaze.resource/hash]
          :opt-un [:blaze.db.tx-cmd/refs
                   :blaze.db.tx-cmd/if-none-exist]))


(defmethod tx-cmd "put" [_]
  (s/keys :req-un [:blaze.db.tx-cmd/op
                   :blaze.db.tx-cmd/type
                   :blaze.resource/id
                   :blaze.resource/hash]
          :opt-un [:blaze.db.tx-cmd/refs
                   :blaze.db.tx-cmd/if-match
                   :blaze.db.tx-cmd/if-none-match]))


(defmethod tx-cmd "delete" [_]
  (s/keys :req-un [:blaze.db.tx-cmd/op
                   :blaze.db.tx-cmd/type
                   :blaze.resource/id]
          :opt-un [:blaze.db.tx-cmd/if-match]))


(s/def :blaze.db.tx-log.kafka/tx-cmd
  (s/multi-spec tx-cmd :op))


(s/def :blaze.db.tx-log.kafka/tx-cmds
  (s/coll-of :blaze.db.tx-log.kafka/tx-cmd :kind vector?))
