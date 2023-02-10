(ns blaze.db.tx-log.spec
  (:require
    [blaze.async.comp :as ac]
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


(s/def :blaze.db.tx-cmd/type
  :fhir.resource/type)


(s/def :blaze.db.tx-cmd/resource
  ac/completable-future?)


(s/def :blaze.db.tx-cmd/refs
  (s/coll-of :blaze.fhir/local-ref-tuple))


(s/def :blaze.db/t
  (s/and int? #(<= 0 % 0xFFFFFFFFFFFFFF)))


(s/def :blaze.db.tx-cmd/if-none-exist
  (s/coll-of :blaze.db.query/search-clause :kind vector? :min-count 1))


(s/def :blaze.db.tx-cmd/if-match
  :blaze.db/t)


(s/def :blaze.db.tx-cmd/if-none-match
  (s/or :any #{"*"} :t :blaze.db/t))


(defmulti tx-cmd "Transaction command" :op)


(defmethod tx-cmd "create" [_]
  (s/keys :req-un [:blaze.db.tx-cmd/op
                   :blaze.db.tx-cmd/type
                   :blaze.resource/id
                   :blaze.resource/hash
                   :blaze.db.tx-cmd/resource]
          :opt-un [:blaze.db.tx-cmd/refs
                   :blaze.db.tx-cmd/if-none-exist]))


(defmethod tx-cmd "put" [_]
  (s/keys :req-un [:blaze.db.tx-cmd/op
                   :blaze.db.tx-cmd/type
                   :blaze.resource/id
                   :blaze.resource/hash
                   :blaze.db.tx-cmd/resource]
          :opt-un [:blaze.db.tx-cmd/refs
                   :blaze.db.tx-cmd/if-match
                   :blaze.db.tx-cmd/if-none-match]))


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


(defmulti submit-tx-cmd "Submit transaction command" :op)


(s/def :blaze.db.submit-tx-cmd/resource
  :blaze/resource)


(defmethod submit-tx-cmd "create" [_]
  (s/keys :req-un [:blaze.db.tx-cmd/op
                   :blaze.db.tx-cmd/type
                   :blaze.resource/id
                   :blaze.db.submit-tx-cmd/resource]
          :opt-un [:blaze.db.tx-cmd/refs
                   :blaze.db.tx-cmd/if-none-exist]))


(defmethod submit-tx-cmd "put" [_]
  (s/keys :req-un [:blaze.db.tx-cmd/op
                   :blaze.db.tx-cmd/type
                   :blaze.resource/id
                   :blaze.db.submit-tx-cmd/resource]
          :opt-un [:blaze.db.tx-cmd/refs
                   :blaze.db.tx-cmd/if-match
                   :blaze.db.tx-cmd/if-none-match]))


(defmethod submit-tx-cmd "delete" [_]
  (s/keys :req-un [:blaze.db.tx-cmd/op
                   :blaze.db.tx-cmd/type
                   :blaze.resource/id]
          :opt-un [:blaze.db.tx-cmd/if-match]))


(s/def :blaze.db/submit-tx-cmd
  (s/multi-spec submit-tx-cmd :op))


(s/def :blaze.db/submit-tx-cmds
  (s/coll-of :blaze.db/submit-tx-cmd :kind vector?))
