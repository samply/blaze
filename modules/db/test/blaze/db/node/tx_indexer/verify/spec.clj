(ns blaze.db.node.tx-indexer.verify.spec
  (:require
   [blaze.db.node.tx-indexer.verify :as-alias verify]
   [blaze.db.spec]
   [blaze.db.tx-log.spec]
   [blaze.fhir.hash.spec]
   [blaze.fhir.spec.spec]
   [clojure.spec.alpha :as s]))

(s/def ::verify/db-before
  :blaze.db/db)

(s/def ::verify/read-only-matcher
  :blaze.db/matcher)

(s/def ::verify/context
  (s/keys :req-un [::verify/db-before ::verify/read-only-matcher]))

(s/def ::verify/op
  #{"create" "hold" "put" "keep" "delete" "delete-history" "purge"})

(defmulti terminal-tx-cmd "Terminal transaction command after expansion." :op)

(defmethod terminal-tx-cmd "create" [_]
  (s/keys :req-un [::verify/op
                   :fhir.resource/type
                   :blaze.resource/id
                   :blaze.resource/hash]
          :opt-un [:blaze.db.tx-cmd/refs
                   :blaze.db.tx-cmd/if-none-exist]))

(defmethod terminal-tx-cmd "hold" [_]
  (s/keys :req-un [::verify/op
                   :fhir.resource/type
                   :blaze.resource/id
                   :blaze.db.tx-cmd/if-none-exist]))

(defmethod terminal-tx-cmd "put" [_]
  (s/keys :req-un [::verify/op
                   :fhir.resource/type
                   :blaze.resource/id
                   :blaze.resource/hash]
          :opt-un [:blaze.db.tx-cmd/refs
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

(defmethod terminal-tx-cmd "purge" [_]
  (s/keys :req-un [::verify/op
                   :fhir.resource/type
                   :blaze.resource/id]
          :opt-un [:blaze.db.tx-cmd/check-refs]))

(s/def ::verify/tx-cmd
  (s/multi-spec terminal-tx-cmd :op))

(s/def ::verify/tx-cmds
  (s/coll-of ::verify/tx-cmd :kind vector?))
