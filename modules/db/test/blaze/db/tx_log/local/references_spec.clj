(ns blaze.db.tx-log.local.references-spec
  (:require
    [blaze.db.tx-log.local.references :as references]
    [blaze.db.tx-log.spec]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]))


(s/fdef references/extract-references
  :args (s/cat :resource :blaze/resource)
  :ret (s/coll-of :blaze.db/local-ref))
