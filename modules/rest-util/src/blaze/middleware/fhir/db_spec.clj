(ns blaze.middleware.fhir.db-spec
  (:require
    [blaze.middleware.fhir.db :as db]
    [clojure.spec.alpha :as s]))


(s/fdef db/wrap-db
  :args (s/cat :handler ifn? :node :blaze.db/node :timeout (s/? pos-int?)))
