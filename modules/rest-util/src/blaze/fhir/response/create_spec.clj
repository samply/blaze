(ns blaze.fhir.response.create-spec
  (:require
    [blaze.db.api-spec]
    [blaze.fhir.response.create :as create]
    [clojure.spec.alpha :as s]
    [reitit.core :as reitit]))


(s/fdef create/build-response
  :args (s/cat :base-url string?
               :router reitit/router?
               :return-preference (s/nilable string?)
               :db :blaze.db/db
               :old-handle (s/nilable :blaze.db/resource-handle)
               :new-handle :blaze.db/resource-handle))
