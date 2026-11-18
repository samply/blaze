(ns blaze.interaction.search.params-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.fhir.search-clauses-spec]
   [blaze.fhir.spec.spec]
   [blaze.handler.fhir.util-spec]
   [blaze.interaction.search.params :as params]
   [blaze.interaction.util-spec]
   [blaze.page-store.spec]
   [blaze.value-set-expander.spec]
   [clojure.spec.alpha :as s]))

(s/fdef params/decode
  :args (s/cat :page-store :blaze/page-store
               :handling (s/nilable (s/and keyword? #(= "blaze.preference.handling" (namespace %))))
               :query-params (s/nilable :ring.request/query-params)
               :paging-params (s/nilable :ring.request/query-params))
  :ret ac/completable-future?)

(s/fdef params/expand-value-sets
  :args (s/cat :value-set-expander :blaze/value-set-expander
               :type :fhir.resource/type
               :params map?)
  :ret ac/completable-future?)
