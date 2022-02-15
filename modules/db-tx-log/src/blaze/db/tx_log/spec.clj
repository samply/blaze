(ns blaze.db.tx-log.spec
  (:require
    [blaze.db.resource-store.spec]
    [blaze.db.tx-log :as tx-log]
    [blaze.fhir.spec]
    [blaze.spec]
    [clojure.spec.alpha :as s]))


(s/def :blaze.db/tx-log
  #(satisfies? tx-log/TxLog %))


(s/def :blaze.db.tx-log/queue
  #(satisfies? tx-log/Queue %))


(s/def :blaze.db.tx-cmd/op
  #{"create" "put" "delete"})


(s/def :blaze.db.tx-cmd/ref
  (s/or :local-ref :blaze.fhir/local-ref-tuple
        :conditional-ref (s/tuple :fhir.resource/type :blaze.db.query/clauses)))


(s/def :blaze.db.tx-cmd/refs
  (s/coll-of :blaze.db.tx-cmd/ref))


(s/def :blaze.db/t
  (s/and int? #(<= 0 % 0xFFFFFFFFFFFFFF)))


(s/def :blaze.db.tx-cmd/if-none-exist
  :blaze.db.query/clauses)


(s/def :blaze.db.tx-cmd/if-match
  :blaze.db/t)


(defmulti tx-cmd "Transaction command" :op)


(defmethod tx-cmd "create" [_]
  (s/keys :req-un [:blaze.db.tx-cmd/op
                   :fhir.resource/type
                   :blaze.resource/id
                   :blaze.resource/hash]
          :opt-un [:blaze.db.tx-cmd/refs
                   :blaze.db.tx-cmd/if-none-exist]))


(defmethod tx-cmd "put" [_]
  (s/keys :req-un [:blaze.db.tx-cmd/op
                   :fhir.resource/type
                   :blaze.resource/id
                   :blaze.resource/hash]
          :opt-un [:blaze.db.tx-cmd/refs
                   :blaze.db.tx-cmd/if-match]))


(defmethod tx-cmd "delete" [_]
  (s/keys :req-un [:blaze.db.tx-cmd/op
                   :fhir.resource/type
                   :blaze.resource/id]
          :opt-un [:blaze.db.tx-cmd/if-match]))


(s/def :blaze.db/tx-cmd
  (s/multi-spec tx-cmd :op))


(s/def :blaze.db/tx-cmds
  (s/coll-of :blaze.db/tx-cmd :kind vector?))


(s/def :blaze.db/tx-data
  (s/keys :req-un [:blaze.db/t :blaze.db.tx/instant :blaze.db/tx-cmds]))
