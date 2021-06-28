(ns blaze.db.resource-store.cassandra.log
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
  (str "Open Cassandra resource store with the following settings: "
       (format-config config)))
