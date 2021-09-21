(ns blaze.db.tx-log.local.codec-spec
  (:require
    [blaze.db.impl.byte-buffer :as bb]
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
  :args (s/alt :0 (s/cat) :2 (s/cat :kb bb/byte-buffer? :vb bb/byte-buffer?)))
