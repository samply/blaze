(ns blaze.db.impl.index.patient-last-change-spec
  (:require
    [blaze.db.impl.codec-spec]
    [blaze.db.impl.index.patient-last-change :as plc]
    [blaze.db.kv-spec]
    [blaze.db.tx-log.spec]
    [blaze.fhir.hash.spec]
    [clojure.spec.alpha :as s]))


(s/fdef plc/index-entry
  :args (s/cat :patient-id :blaze.db/id-byte-string :t :blaze.db/t)
  :ret :blaze.db.kv/put-entry-w-cf)


(s/fdef plc/last-change-t
  :args (s/cat :plci :blaze.db/kv-iterator
               :patient-id :blaze.db/id-byte-string
               :t :blaze.db/t)
  :ret (s/nilable :blaze.db/t))
