(ns blaze.terminology-service-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service.code-system-validate-code :as-alias cs-validate-code]
   [blaze.terminology-service.expand-value-set :as-alias expand-vs]
   [blaze.terminology-service.spec]
   [blaze.terminology-service.value-set-validate-code :as-alias vs-validate-code]
   [clojure.spec.alpha :as s]))

(s/fdef ts/code-systems
  :args (s/cat :terminology-service :blaze/terminology-service)
  :ret ac/completable-future?)

(s/fdef ts/code-system-validate-code
  :args (s/cat :terminology-service :blaze/terminology-service
               :request ::cs-validate-code/request)
  :ret ac/completable-future?)

(s/fdef ts/expand-value-set
  :args (s/cat :terminology-service :blaze/terminology-service
               :request ::expand-vs/request)
  :ret ac/completable-future?)

(s/fdef ts/value-set-validate-code
  :args (s/cat :terminology-service :blaze/terminology-service
               :request ::vs-validate-code/request)
  :ret ac/completable-future?)
