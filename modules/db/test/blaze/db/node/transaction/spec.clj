(ns blaze.db.node.transaction.spec
  (:require
   [blaze.db.spec]
   [blaze.db.tx-log.spec]
   [blaze.fhir.hash.spec]
   [blaze.fhir.spec.spec]
   [clojure.spec.alpha :as s]))

(s/def :blaze.db.node.transaction/context
  (s/keys :opt [:blaze.db/enforce-referential-integrity]))
