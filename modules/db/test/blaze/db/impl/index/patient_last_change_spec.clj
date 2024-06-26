(ns blaze.db.impl.index.patient-last-change-spec
  (:require
   [blaze.db.impl.codec-spec]
   [blaze.db.impl.index.patient-last-change :as plc]
   [blaze.db.impl.index.patient-last-change.spec]
   [blaze.db.kv-spec]
   [blaze.db.tx-log.spec]
   [blaze.fhir.hash.spec]
   [clojure.spec.alpha :as s]))

(s/fdef plc/index-entry
  :args (s/cat :patient-id :blaze.db/id-byte-string :t :blaze.db/t)
  :ret :blaze.db.kv/put-entry)

(s/fdef plc/last-change-t
  :args (s/cat :plci :blaze.db.kv/iterator
               :patient-id :blaze.db/id-byte-string
               :t :blaze.db/t)
  :ret (s/nilable :blaze.db/t))

(s/fdef plc/state
  :args (s/cat :kv-store :blaze.db/kv-store)
  :ret ::plc/state)

(s/fdef plc/state-index-entry
  :args (s/cat :state ::plc/state)
  :ret :blaze.db.kv/put-entry)
