(ns blaze.db.impl.batch-db.patient-everything-spec
  (:require
   [blaze.coll.spec :as cs]
   [blaze.db.impl.batch-db.patient-everything :as pe]
   [blaze.db.impl.batch-db.spec]
   [blaze.db.impl.search-param.date-spec]
   [blaze.fhir.spec.type.system.spec]
   [clojure.spec.alpha :as s]))

(s/fdef pe/patient-everything
  :args (s/cat :batch-db :blaze.db.impl/batch-db
               :patient-handle :blaze.db/resource-handle
               :start (s/nilable :system/date)
               :end (s/nilable :system/date))
  :ret (cs/coll-of :blaze.db/resource-handle))
