(ns blaze.db.tx-log.local.codec-spec
  (:require
   [blaze.byte-buffer :refer [byte-buffer?]]
   [blaze.db.kv.spec]
   [blaze.db.spec]
   [blaze.db.tx-log.local.codec :as codec]
   [blaze.db.tx-log.spec]
   [clojure.spec.alpha :as s]))

(s/fdef codec/encode-key
  :args (s/cat :t :blaze.db/t)
  :ret bytes?)

(s/fdef codec/encode-tx-data
  :args (s/cat :instant :blaze.db.tx/instant :tx-cmds :blaze.db/tx-cmds
               :permissions (s/nilable :blaze.db/permissions))
  :ret bytes?)

(s/fdef codec/decode-tx-data
  :args (s/cat :buffers (s/tuple byte-buffer? byte-buffer?))
  :ret (s/nilable map?))

(s/fdef codec/encode-entry
  :args (s/cat :t :blaze.db/t :instant :blaze.db.tx/instant
               :tx-cmds :blaze.db/tx-cmds
               :permissions (s/nilable :blaze.db/permissions))
  :ret :blaze.db.kv/put-entry)
