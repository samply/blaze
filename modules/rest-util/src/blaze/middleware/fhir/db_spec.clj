(ns blaze.middleware.fhir.db-spec
  (:require
   [blaze.db.spec]
   [blaze.middleware.fhir.db :as db]
   [clojure.spec.alpha :as s]))

(s/fdef db/wrap-db
  :args (s/cat :handler fn? :node :blaze.db/node :timeout pos-int?)
  :ret fn?)

(s/fdef db/wrap-snapshot-db
  :args (s/cat :handler fn? :node :blaze.db/node :timeout pos-int?)
  :ret fn?)
