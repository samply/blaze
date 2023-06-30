(ns blaze.db.tx-log.spec
  (:require
    [blaze.db.resource-store.spec]
    [blaze.db.tx-log :as tx-log]
    [blaze.fhir.spec]
    [blaze.spec]
    [clojure.spec.alpha :as s]))


(defn tx-log? [x]
  (satisfies? tx-log/TxLog x))


(s/def :blaze.db/tx-log
  tx-log?)


(defn queue? [x]
  (satisfies? tx-log/Queue x))


(s/def :blaze.db.tx-log/queue
  queue?)


(s/def :blaze.db.tx-cmd/op
  #{"create" "put" "keep" "delete"})


(s/def :blaze.db.tx-cmd/type
  :fhir.resource/type)


(s/def :blaze.db.tx-cmd/refs
  (s/coll-of :blaze.fhir/local-ref-tuple))


(s/def :blaze.db/t
  (s/and int? #(<= 0 % 0xFFFFFFFFFFFFFF)))


(s/def :blaze.db.tx-cmd/if-none-exist
  (s/coll-of :blaze.db.query/search-clause :kind vector? :min-count 1))


(s/def :blaze.db.tx-cmd/if-match
  (s/or :t :blaze.db/t :ts (s/coll-of :blaze.db/t :kind vector? :min-count 1)))


(s/def :blaze.db.tx-cmd/if-none-match
  (s/or :any #{"*"} :t :blaze.db/t))


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


(defmethod tx-cmd "keep" [_]
  (s/keys :req-un [:blaze.db.tx-cmd/op
                   :blaze.db.tx-cmd/type
                   :blaze.resource/id
                   :blaze.resource/hash]
          :opt-un [:blaze.db.tx-cmd/if-match]))


(defmethod tx-cmd "delete" [_]
  (s/keys :req-un [:blaze.db.tx-cmd/op
                   :blaze.db.tx-cmd/type
                   :blaze.resource/id]
          :opt-un [:blaze.db.tx-cmd/if-match]))


(s/def :blaze.db/tx-cmd
  (s/multi-spec tx-cmd :op))


(s/def :blaze.db/tx-cmds
  (s/coll-of :blaze.db/tx-cmd :kind vector?))


(s/def :blaze.db/tx-data
  (s/keys :req-un [:blaze.db/t :blaze.db.tx/instant :blaze.db/tx-cmds]))
