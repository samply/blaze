(ns blaze.server.spec
  (:require
    [clojure.spec.alpha :as s]))


(s/def :blaze.server/port
  (s/and nat-int? #(<= % 65535)))


(s/def :blaze.server/handler
  fn?)


(s/def :blaze.server/version
  string?)


(s/def :blaze.server/async?
  boolean?)


(s/def :blaze.server/min-threads
  (s/and nat-int? #(<= % 100)))


(s/def :blaze.server/max-threads
  (s/and nat-int? #(<= % 100)))
