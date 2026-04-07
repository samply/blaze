(ns blaze.openid-client.token-provider-spec
  (:require
   [blaze.openid-client.token-provider :as tp]
   [blaze.openid-client.token-provider.spec]
   [clojure.spec.alpha :as s]))

(s/fdef tp/current-token
  :args (s/cat :token-provider :blaze.openid-client/token-provider))
