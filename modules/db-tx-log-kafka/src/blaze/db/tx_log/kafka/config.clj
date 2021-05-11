(ns blaze.db.tx-log.kafka.config
  (:require
    [clojure.string :as str]))


(def ^:private ssl-keys
  [:ssl-truststore-location :ssl-truststore-password
   :ssl-keystore-location :ssl-keystore-password
   :ssl-key-password])


(def ^:private kv-mapper
  (map (fn [[k v]] [(str/replace (name k) \- \.) (str v)])))


(defn- map-config [config keys]
  (into {} kv-mapper (select-keys config keys)))


(def ^:private default-producer-config
  {"enable.idempotence" "true"
   "acks" "all"
   "compression.type" "snappy"
   "delivery.timeout.ms" "60000"})


(def ^:private producer-config-keys
  (conj ssl-keys :bootstrap-servers :max-request-size :compression-type
        :security-protocol))


(defn producer-config [config]
  (merge default-producer-config (map-config config producer-config-keys)))


(def ^:private default-consumer-config
  {"enable.auto.commit" "false"
   "isolation.level" "read_committed"
   "auto.offset.reset" "earliest"})


(def ^:private consumer-config-keys
  (conj ssl-keys :bootstrap-servers :security-protocol))


(defn consumer-config [config]
  (merge default-consumer-config (map-config config consumer-config-keys)))
