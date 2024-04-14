(ns blaze.db.tx-log.kafka.log
  (:require
   [blaze.db.tx-log.kafka :as-alias kafka]
   [blaze.db.tx-log.kafka.util :as u]
   [clojure.string :as str]))

(defn- tx-log-name [key]
  (cond->> "Kafka transaction log"
    (vector? key)
    (str (u/integrant-key-name-part key) " ")))

(defn- format-value [k v]
  (if (str/ends-with? k "password") "[hidden]" v))

(defn- format-config [config]
  (->> config
       (remove (fn [[k]] (= ::kafka/last-t-executor k)))
       (keep
        (fn [[k v]] (when (some? v) (str (name k) " = " (format-value k v)))))
       (str/join ", ")))

(defn init-msg [key config]
  (str "Open " (tx-log-name key) " with the following settings: "
       (format-config config)))
