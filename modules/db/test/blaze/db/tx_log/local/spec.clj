(ns blaze.db.tx-log.local.spec
  (:require
    [blaze.db.tx-log.spec]
    [blaze.fhir.hash.spec]
    [blaze.fhir.spec.spec]
    [clojure.spec.alpha :as s]))


(defmulti tx-cmd "Transaction command" :op)


(defmethod tx-cmd "create" [_]
  (s/keys :req-un [:blaze.db.tx-cmd/op
                   :blaze.db.tx-cmd/type
                   :blaze.resource/id
                   :blaze.resource/hash]
          :opt-un [:blaze.db.tx-cmd/refs
                   :blaze.db.tx-cmd/if-none-exist]))


(defmethod tx-cmd "put" [_]
  (s/keys :req-un [:blaze.db.tx-cmd/op
                   :blaze.db.tx-cmd/type
                   :blaze.resource/id
                   :blaze.resource/hash]
          :opt-un [:blaze.db.tx-cmd/refs
                   :blaze.db.tx-cmd/if-match
                   :blaze.db.tx-cmd/if-none-match]))


(defmethod tx-cmd "delete" [_]
  (s/keys :req-un [:blaze.db.tx-cmd/op
                   :blaze.db.tx-cmd/type
                   :blaze.resource/id]
          :opt-un [:blaze.db.tx-cmd/if-match]))


(s/def :blaze.db.tx-log.local/tx-cmd
  (s/multi-spec tx-cmd :op))


(s/def :blaze.db.tx-log.local/tx-cmds
  (s/coll-of :blaze.db.tx-log.local/tx-cmd :kind vector?))
