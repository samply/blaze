(ns blaze.db.node.tx-indexer.verify.impl-spec
  (:require
    [blaze.db.node.tx-indexer.verify.impl :as impl]
    [blaze.db.spec]
    [blaze.db.tx-log.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]))


(defmulti ext-tx-cmd "Extended transaction command" :op)


(defmethod ext-tx-cmd "create" [_]
  (s/keys :req-un [:fhir.resource/type
                   :blaze.resource/id
                   :blaze.resource/hash]
          :opt-un [:blaze.db.tx-cmd/refs
                   :blaze.db.tx-cmd/if-none-exist]))


(defmethod ext-tx-cmd "put" [_]
  (s/keys :req-un [:fhir.resource/type
                   :blaze.resource/id
                   :blaze.resource/hash]
          :opt-un [:blaze.db.tx-cmd/refs
                   :blaze.db.tx-cmd/if-match]))


(defmethod ext-tx-cmd "delete" [_]
  (s/keys :req-un [:fhir.resource/type
                   :blaze.resource/id]
          :opt-un [:blaze.db.tx-cmd/if-match]))


(defmethod ext-tx-cmd "hold" [_]
  (s/keys :req-un [:fhir.resource/type
                   :blaze.resource/id]))


(s/def :blaze.db/ext-tx-cmd
  (s/multi-spec ext-tx-cmd :op))


(s/def :blaze.db/ext-tx-cmds
  (s/coll-of :blaze.db/ext-tx-cmd :kind vector?))


(s/fdef impl/resolve-conditional-create
  :args (s/cat :db :blaze.db/db :commands :blaze.db/tx-cmds)
  :ret (s/or :commands :blaze.db/tx-cmds :anomaly ::anom/anomaly))


(s/fdef impl/detect-duplicate-commands
  :args (s/cat :commands :blaze.db/ext-tx-cmds)
  :ret (s/nilable ::anom/anomaly))


(s/fdef impl/verify-commands
  :args (s/cat :db :blaze.db/db :commands :blaze.db/tx-cmds)
  :ret (s/or :commands :blaze.db/tx-cmds :anomaly ::anom/anomaly))


(s/fdef impl/resolve-conditional-refs
  :args (s/cat :db :blaze.db/db :commands :blaze.db/tx-cmds)
  :ret (s/or :commands :blaze.db/tx-cmds :anomaly ::anom/anomaly))
