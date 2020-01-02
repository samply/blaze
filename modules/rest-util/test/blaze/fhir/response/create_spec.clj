(ns blaze.fhir.response.create-spec
  (:require
    [blaze.db.api-spec]
    [blaze.fhir.response.create :as create]
    [clojure.spec.alpha :as s]
    [reitit.core :as reitit]))


(s/fdef create/build-created-response
  :args (s/cat :router reitit/router? :return-preference (s/nilable string?)
               :db :blaze.db/db :type string? :id string?))
