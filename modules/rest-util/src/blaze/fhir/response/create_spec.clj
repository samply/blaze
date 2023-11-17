(ns blaze.fhir.response.create-spec
  (:require
   [blaze.db.api-spec]
   [blaze.db.spec]
   [blaze.fhir.response.create :as create]
   [blaze.http.spec]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [reitit.core :as reitit]))

(s/fdef create/build-response
  :args (s/cat :context (s/keys :req [:blaze/base-url :blaze/db ::reitit/router]
                                :opt [:blaze.preference/return])
               :tx-op (s/nilable :blaze.db/tx-op)
               :old-handle (s/nilable :blaze.db/resource-handle)
               :new-handle :blaze.db/resource-handle))
