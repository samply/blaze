(ns blaze.admin-api.spec
  (:require
   [blaze.admin-api :as-alias admin-api]
   [blaze.admin-api.feature :as-alias feature]
   [blaze.admin-api.setting :as-alias setting]
   [blaze.db.kv :as-alias kv]
   [blaze.db.kv.rocksdb.spec]
   [clojure.spec.alpha :as s]))

(s/def ::admin-api/dbs
  (s/map-of string? ::kv/rocksdb))

(s/def ::admin-api/settings
  (s/coll-of ::admin-api/setting))

(s/def ::admin-api/setting
  (s/keys :req-un [::setting/name ::setting/value ::setting/default-value]))

(s/def ::setting/name
  string?)

(s/def ::setting/value
  any?)

(s/def ::setting/default-value
  any?)

(s/def ::admin-api/features
  (s/map-of simple-keyword? ::admin-api/feature))

(s/def ::admin-api/feature
  (s/keys :req-un [::feature/name ::feature/toggle ::feature/enabled]))

(s/def ::feature/name
  string?)

(s/def ::feature/toggle
  string?)

(s/def ::feature/enabled
  boolean?)
