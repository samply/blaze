(ns blaze.interaction.search.params-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.fhir.spec.spec]
   [blaze.handler.fhir.util-spec]
   [blaze.interaction.search.params :as params]
   [blaze.interaction.util-spec]
   [blaze.page-store.spec]
   [clojure.spec.alpha :as s]))

(s/fdef params/decode
  :args (s/cat :page-store :blaze/page-store
               :handling (s/nilable (s/and keyword? #(= "blaze.preference.handling" (namespace %))))
               :query-params (s/nilable :ring.request/query-params))
  :ret ac/completable-future?)
