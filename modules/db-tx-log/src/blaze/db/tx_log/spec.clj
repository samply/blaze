(ns blaze.db.tx-log.spec
  (:require
    [blaze.db.resource-store.spec]
    [blaze.db.tx-log :as tx-log]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]))


(s/def :blaze.db/tx-log
  #(satisfies? tx-log/TxLog %))


(s/def :blaze.db.tx-log/queue
  #(satisfies? tx-log/Queue %))


(s/def :blaze.db.tx-cmd/op
  #{"create" "put" "delete"})


(s/def :blaze.db.tx-cmd/type
  :blaze.resource/resourceType)


(s/def :blaze.db/local-ref
  (s/tuple :blaze.resource/resourceType :blaze.resource/id))


(s/def :blaze.db.tx-cmd/refs
  (s/coll-of :blaze.db/local-ref))


(s/def :blaze.db/t
  nat-int?)


(s/def :blaze.db.tx-cmd/if-match
  :blaze.db/t)


(defmulti tx-cmd "Transaction command" :op)


(defmethod tx-cmd "create" [_]
  (s/keys :req-un [:blaze.db.tx-cmd/op
                   :blaze.db.tx-cmd/type
                   :blaze.resource/id
                   :blaze.db.resource/hash]
          :opt-un [:blaze.db.tx-cmd/refs]))


(defmethod tx-cmd "put" [_]
  (s/keys :req-un [:blaze.db.tx-cmd/op
                   :blaze.db.tx-cmd/type
                   :blaze.resource/id
                   :blaze.db.resource/hash]
          :opt-un [:blaze.db.tx-cmd/refs
                   :blaze.db.tx-cmd/if-match]))


(defmethod tx-cmd "delete" [_]
  (s/keys :req-un [:blaze.db.tx-cmd/op
                   :blaze.db.tx-cmd/type
                   :blaze.resource/id
                   :blaze.db.resource/hash]
          :opt-un [:blaze.db.tx-cmd/if-match]))


(s/def :blaze.db/tx-cmd
  (s/multi-spec tx-cmd :op))


(s/def :blaze.db/tx-cmds
  (s/coll-of :blaze.db/tx-cmd :kind vector? :min-count 1))


(s/def :blaze.db/tx-data
  (s/keys :req-un [:blaze.db/t :blaze.db.tx/instant :blaze.db/tx-cmds]))
