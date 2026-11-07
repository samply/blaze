(ns blaze.db.tx-log.kafka.codec-spec
  (:require
   [blaze.db.permissions.spec]
   [blaze.db.tx-log.kafka.codec :as codec]
   [clojure.spec.alpha :as s]))

(s/fdef codec/encode-permissions
  :args (s/cat :permissions :blaze.db/permissions)
  :ret bytes?)

(s/fdef codec/decode-permissions
  :args (s/cat :value bytes?)
  :ret any?)
