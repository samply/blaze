(ns blaze.db.tx-log.kafka.util
  (:require
   [blaze.db.tx-log.kafka.codec :as codec]
   [blaze.db.tx-log.util :as tx-log-u]
   [clojure.string :as str]
   [taoensso.timbre :as log])
  (:import
   [java.time Instant]
   [org.apache.kafka.clients.consumer ConsumerRecord]
   [org.apache.kafka.common.header Header]
   [org.apache.kafka.common.record TimestampType]))

(set! *warn-on-reflection* true)

(defn- record->t
  "Returns the point in time `t` of the transaction data received by a consumer.

  The `t` is calculated from the offset by incrementing it because Kafka
  offsets start at zero were `t`s start at one."
  [record]
  (inc (.offset ^ConsumerRecord record)))

(defn- invalid-timestamp-type-msg [t timestamp-type]
  (format "Skip transaction with point in time of %d because the timestamp type is `%s` instead of `LogAppendTime`."
          t timestamp-type))

(defn- permissions-header ^Header [^ConsumerRecord record]
  (.lastHeader (.headers record) codec/permissions-header))

(defn- tx-data
  "Returns the transaction data of `record` or nil if it is invalid.

  A record without permissions header has no permissions. A record with an
  invalid permissions header is skipped like one with invalid transaction
  commands."
  [t ^ConsumerRecord record]
  (let [header (permissions-header record)]
    (tx-log-u/valid-tx-data
     (cond-> {:t t
              :instant (Instant/ofEpochMilli (.timestamp record))
              :tx-cmds (.value record)}
       header (assoc :permissions (codec/decode-permissions (.value header)))))))

(def record-transformer
  (keep
   (fn [^ConsumerRecord record]
     (let [t (record->t record)]
       (if (= TimestampType/LOG_APPEND_TIME (.timestampType record))
         (tx-data t record)
         (log/warn (invalid-timestamp-type-msg t (.timestampType record))))))))

(defn integrant-key-name-part [[_ key]]
  (-> key namespace (str/split #"\.") last))

(defn node-name
  "Returns the name of the node the component with Integrant `key` belongs to.

  Defaults to `main` for a non-composite key, because such a key denotes the
  only node of a system."
  [key]
  (if (vector? key) (integrant-key-name-part key) "main"))
