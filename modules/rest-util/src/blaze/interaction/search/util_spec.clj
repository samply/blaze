(ns blaze.interaction.search.util-spec
  (:require
   [blaze.fhir.spec.spec]
   [blaze.handler.fhir.util-spec]
   [blaze.interaction.search.util :as search-util]
   [blaze.interaction.search.util.spec]
   [clojure.spec.alpha :as s]))

(s/fdef search-util/match-entry
  :args (s/cat :context ::search-util/context :resource :fhir/Resource
               :match-extension (s/? (s/nilable (s/coll-of :fhir/Extension :kind vector?))))
  :ret :fhir.Bundle/entry)

(s/fdef search-util/include-entry
  :args (s/cat :context ::search-util/context :resource :fhir/Resource))

(s/fdef search-util/outcome-entry
  :args (s/cat :context ::search-util/context :resource :fhir/OperationOutcome))
