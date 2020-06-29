(ns blaze.db.tx-log.kafka.spec
  (:require
    [clojure.spec.alpha :as s]))


(s/def :blaze.db.tx-log.kafka/bootstrap-servers
  string?)


(s/def :blaze.db.tx-log.kafka/max-request-size
  nat-int?)
