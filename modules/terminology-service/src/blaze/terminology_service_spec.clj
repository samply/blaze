(ns blaze.terminology-service-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service.expand-value-set :as-alias expand-vs]
   [blaze.terminology-service.spec]
   [clojure.spec.alpha :as s]))

(s/fdef ts/expand-value-set
  :args (s/cat :terminology-service :blaze/terminology-service
               :request ::expand-vs/request)
  :ret ac/completable-future?)
