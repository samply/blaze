(ns blaze.interaction.search.util-spec
  (:require
    [blaze.fhir.spec.spec]
    [blaze.http.spec]
    [blaze.interaction.search.util :as search-util]
    [blaze.spec]
    [clojure.spec.alpha :as s]
    [reitit.core :as reitit]))


(s/fdef search-util/entry
  :args (s/cat :context (s/keys :req [:blaze/base-url ::reitit/router])
               :resource :blaze/resource
               :mode (s/? map?)))
