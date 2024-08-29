(ns blaze.middleware.fhir.decrypt-page-id-spec
  (:require
   [blaze.handler.fhir.util.spec]
   [blaze.middleware.fhir.decrypt-page-id :as decrypt-page-id]
   [blaze.page-id-cipher.spec]
   [blaze.spec]
   [clojure.spec.alpha :as s]))

(s/fdef decrypt-page-id/wrap-decrypt-page-id
  :args (s/cat :handler fn? :page-id-cipher :blaze/page-id-cipher)
  :ret fn?)

(s/fdef decrypt-page-id/encrypt
  :args (s/cat :page-id-cipher :blaze/page-id-cipher
               :query-params (s/nilable :ring.request/query-params))
  :ret :blaze/page-id)
