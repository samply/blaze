(ns blaze.middleware.fhir.page-id-spec
  (:require
   [blaze.handler.fhir.util.spec]
   [blaze.middleware.fhir.page-id :as page-id]
   [blaze.page-id-cipher.spec]
   [blaze.spec]
   [clojure.spec.alpha :as s]))

(s/fdef page-id/wrap-decrypt-page-id
  :args (s/cat :handler fn? :page-id-cipher :blaze/page-id-cipher)
  :ret fn?)

(s/fdef page-id/encrypt
  :args (s/cat :page-id-cipher :blaze/page-id-cipher
               :query-params (s/nilable :ring.request/query-params))
  :ret :blaze/page-id)
