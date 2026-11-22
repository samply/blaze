(ns blaze.openid-auth.jwt-spec
  (:require
   [blaze.openid-auth.jwt :as jwt]
   [blaze.openid-auth.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef jwt/verify
  :args (s/cat :keys :blaze.openid-auth/public-keys
               :opts :blaze.openid-auth/verify-opts
               :now int?
               :token string?)
  :ret (s/or :claims map? :anomaly ::anom/anomaly))
