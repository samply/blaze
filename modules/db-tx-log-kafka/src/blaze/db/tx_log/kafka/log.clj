(ns blaze.db.tx-log.kafka.log
  (:require
    [clojure.string :as str]))


(defn- format-value [k v]
  (if (str/ends-with? k "password") "[hidden]" v))


(defn- format-config [config]
  (->> config
       (keep
         (fn [[k v]] (when (some? v) (str (name k) " = " (format-value k v)))))
       (str/join ", ")))


(defn init-msg [config]
  (str "Open Kafka transaction log with the following settings: "
       (format-config config)))
