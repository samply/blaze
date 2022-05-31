(ns blaze.db.tx-log.kafka.util
  (:require
    [blaze.db.tx-log.spec]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [taoensso.timbre :as log])
  (:import
    [java.time Instant]
    [org.apache.kafka.clients.consumer ConsumerRecord]
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


(defn- invalid-tx-data-msg [t cause]
  (format "Skip transaction with point in time of %d because tx-data is invalid: %s"
          t (str/replace cause #"\s" " ")))


(def record-transformer
  (mapcat
    (fn [^ConsumerRecord record]
      (let [t (record->t record)]
        (if (= TimestampType/LOG_APPEND_TIME (.timestampType record))
          (let [tx-cmds (.value record)]
            (if (s/valid? :blaze.db/tx-cmds tx-cmds)
              [{:t t
                :instant (Instant/ofEpochMilli (.timestamp record))
                :tx-cmds tx-cmds}]
              (log/warn (invalid-tx-data-msg t (s/explain-str :blaze.db/tx-cmds tx-cmds)))))
          (log/warn (invalid-timestamp-type-msg t (.timestampType record))))))))
