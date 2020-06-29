(ns blaze.server.spec
  (:require
    [blaze.executors :as ex]
    [clojure.spec.alpha :as s]))


(s/def :blaze.server/port
  (s/and nat-int? #(<= % 65535)))


(s/def :blaze.server/executor
  ex/executor?)
