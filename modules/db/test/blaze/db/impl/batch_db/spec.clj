(ns blaze.db.impl.batch-db.spec
  (:require
    [blaze.db.kv.spec]
    [clojure.spec.alpha :as s]))


(s/def ::snapshot
  :blaze.db/kv-snapshot)


(s/def ::raoi
  :blaze.db/kv-iterator)


(s/def ::svri
  :blaze.db/kv-iterator)


(s/def ::rsvi
  :blaze.db/kv-iterator)


(s/def ::cri
  :blaze.db/kv-iterator)


(s/def ::csvri
  :blaze.db/kv-iterator)


(s/def :blaze.db.impl.batch-db/context
  (s/keys :req-un [::snapshot ::raoi ::svri ::rsvi ::cri ::csvri]))
