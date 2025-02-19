(ns blaze.admin-api.spec
  (:require
   [blaze.admin-api :as-alias admin-api]
   [blaze.admin-api.feature :as-alias feature]
   [blaze.admin-api.job :as-alias job]
   [blaze.admin-api.setting :as-alias setting]
   [blaze.db.kv :as-alias kv]
   [blaze.db.kv.rocksdb.spec]
   [blaze.db.spec]
   [clojure.spec.alpha :as s]))

(s/def ::admin-api/admin-node
  :blaze.db/node)

(s/def ::admin-api/read-job-handler
  fn?)

(s/def ::admin-api/history-job-handler
  fn?)

(s/def ::admin-api/search-type-job-handler
  fn?)

(s/def ::admin-api/dbs
  (s/map-of string? ::kv/rocksdb))

(s/def ::admin-api/settings
  (s/coll-of ::admin-api/setting))

(s/def ::admin-api/setting
  (s/keys :req-un [::setting/name]
          :opt-un [::setting/value ::setting/masked ::setting/default-value]))

(s/def ::setting/name
  string?)

(s/def ::setting/value
  any?)

(s/def ::setting/masked
  boolean?)

(s/def ::setting/default-value
  any?)

(s/def ::admin-api/features
  (s/coll-of ::admin-api/feature))

(s/def ::admin-api/feature
  (s/keys :req-un [::feature/name ::feature/toggle ::feature/enabled]))

(s/def ::feature/name
  string?)

(s/def ::feature/toggle
  string?)

(s/def ::feature/enabled
  boolean?)

(s/def ::admin-api/job
  (s/keys :req-un [::job/code]))

(s/def ::job/code
  string?)
