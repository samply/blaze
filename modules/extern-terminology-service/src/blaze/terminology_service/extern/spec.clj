(ns blaze.terminology-service.extern.spec
  (:require
   [blaze.openid-client.token-provider.spec]
   [clojure.spec.alpha :as s]))

(s/def :blaze.terminology-service.extern/base-uri
  string?)
