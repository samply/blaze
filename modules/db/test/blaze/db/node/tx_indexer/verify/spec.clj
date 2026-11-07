(ns blaze.db.node.tx-indexer.verify.spec
  (:require
   [blaze.db.impl.fold :as-alias f]
   [blaze.db.impl.fold.spec]
   [blaze.db.kv.spec]
   [blaze.db.node.stats :as-alias stats]
   [blaze.db.node.stats.spec]
   [blaze.db.node.tx-indexer.util :as-alias tx-u]
   [blaze.db.node.tx-indexer.util.spec]
   [blaze.db.node.tx-indexer.verify :as-alias verify]
   [blaze.db.permissions.spec]
   [blaze.db.spec]
   [blaze.db.tx-log.spec]
   [blaze.fhir.hash.spec]
   [blaze.fhir.spec.spec]
   [clojure.spec.alpha :as s]))

(s/def ::verify/db-before
  :blaze.db/db)

(s/def ::verify/read-only-matcher
  :blaze.db/matcher)

(s/def ::verify/internal-matcher
  :blaze.db/matcher)

(s/def ::verify/permissions
  :blaze.db/permissions)

(s/def ::verify/stats
  ::stats/stats)

(s/def ::verify/pool
  ::f/pool)

(s/def ::verify/entries
  (s/coll-of (s/coll-of :blaze.db.kv/put-entry) :kind vector?))

(s/def ::verify/resource-handle-updates
  (s/coll-of (s/coll-of (s/tuple :blaze.db/resource-handle boolean?)
                        :kind vector?)
             :kind vector?))

(s/def ::verify/distinct-tx-cmds
  boolean?)

(s/def ::verify/context
  (s/keys :req-un [::verify/db-before ::verify/read-only-matcher
                   ::verify/internal-matcher ::verify/stats ::verify/pool]
          :opt-un [::verify/permissions ::verify/distinct-tx-cmds]))

(s/def ::verify/op
  #{"create" "hold" "put" "keep" "delete" "delete-history"
    "delete-history-version" "purge"})

(defmulti terminal-tx-cmd "Terminal transaction command after expansion." :op)

(defmethod terminal-tx-cmd "create" [_]
  (s/keys :req-un [::verify/op
                   :fhir.resource/type
                   :blaze.resource/id
                   :blaze.resource/hash]
          :opt-un [:blaze.db.tx-cmd/refs
                   :blaze.db.tx-cmd/access-control
                   :blaze.db.tx-cmd/if-none-exist]))

(defmethod terminal-tx-cmd "hold" [_]
  (s/keys :req-un [::verify/op
                   :fhir.resource/type
                   :blaze.resource/id
                   :blaze.db.tx-cmd/if-none-exist]
          :opt-un [:blaze.db.tx-cmd/refs]))

(defmethod terminal-tx-cmd "put" [_]
  (s/keys :req-un [::verify/op
                   :fhir.resource/type
                   :blaze.resource/id
                   :blaze.resource/hash]
          :opt-un [:blaze.db.tx-cmd/refs
                   :blaze.db.tx-cmd/access-control
                   :blaze.db.tx-cmd/if-match
                   :blaze.db.tx-cmd/if-none-match]))

(defmethod terminal-tx-cmd "keep" [_]
  (s/keys :req-un [::verify/op
                   :fhir.resource/type
                   :blaze.resource/id
                   :blaze.resource/hash]
          :opt-un [:blaze.db.tx-cmd/if-match]))

(defmethod terminal-tx-cmd "delete" [_]
  (s/keys :req-un [::verify/op
                   :fhir.resource/type
                   :blaze.resource/id]
          :opt-un [:blaze.db.tx-cmd/check-refs]))

(defmethod terminal-tx-cmd "delete-history" [_]
  (s/keys :req-un [::verify/op
                   :fhir.resource/type
                   :blaze.resource/id]))

(defmethod terminal-tx-cmd "delete-history-version" [_]
  (s/keys :req-un [::verify/op
                   :fhir.resource/type
                   :blaze.resource/id
                   :blaze.db/t]))

(s/def ::verify/purged-rev-include-params
  (s/coll-of ::tx-u/rev-include-param :kind set?))

(defmethod terminal-tx-cmd "purge" [_]
  (s/keys :req-un [::verify/op
                   :fhir.resource/type
                   :blaze.resource/id]
          :opt-un [:blaze.db.tx-cmd/check-refs
                   ::verify/purged-rev-include-params]))

(s/def ::verify/tx-cmd
  (s/multi-spec terminal-tx-cmd :op))

(s/def ::verify/tx-cmds
  (s/coll-of ::verify/tx-cmd :kind vector?))
