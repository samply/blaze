(ns blaze.openid-auth.jwk-spec
  (:require
   [blaze.openid-auth.jwk :as jwk]
   [blaze.openid-auth.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef jwk/public-keys
  :args (s/cat :jwks-document any?)
  :ret (s/or :result (s/keys :req-un [:blaze.openid-auth.jwks/keys
                                      :blaze.openid-auth.jwks/skipped])
             :anomaly ::anom/anomaly))
