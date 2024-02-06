(ns blaze.db.tx-log.local.codec-spec
  (:require
   [blaze.byte-buffer :refer [byte-buffer?]]
   [blaze.db.tx-log.local.codec :as codec]
   [blaze.db.tx-log.spec]
   [clojure.spec.alpha :as s]))

(s/fdef codec/encode-key
  :args (s/cat :t :blaze.db/t)
  :ret bytes?)

(s/fdef codec/encode-tx-data
  :args (s/cat :instant :blaze.db.tx/instant :tx-cmds :blaze.db/tx-cmds)
  :ret bytes?)

(s/fdef codec/decode-tx-data
  :args (s/cat :buffers (s/tuple byte-buffer? byte-buffer?)))
